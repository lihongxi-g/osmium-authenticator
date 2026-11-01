package com.safekey.authenticator.totp

import com.safekey.authenticator.model.VaultAccount
import java.net.URLEncoder
import java.util.Base64

/**
 * Builds Google Authenticator's "transfer accounts" QR payloads.
 *
 * The format is the mirror image of [GoogleMigrationParser]:
 *   otpauth-migration://offline?data=<url-encoded base64 protobuf>
 *
 * Encoded by hand (varint + length-delimited), no protobuf dependency. Google
 * never documented the schema; it is the community-standard reverse-engineered
 * one that Aegis and others also speak, which is why the encoder is verified by
 * round-tripping through our own parser.
 *
 * Works on [VaultAccount] because that is what the export path hands over —
 * the same plaintext model the file exporters use.
 *
 * Accounts the format cannot express faithfully are reported instead of being
 * silently mangled:
 *  - Steam Guard (custom alphabet, 5 characters) — Google Authenticator cannot
 *    display it;
 *  - TOTP with a period other than 30 s — the schema has no period field, so
 *    Google Authenticator would silently compute different codes;
 *  - algorithms other than SHA1/SHA256/SHA512 (MD5), digits other than 6/8,
 *    empty or undecodable secrets.
 */
object GoogleMigrationEncoder {

    /** Google Authenticator reads at most this many accounts per QR code. */
    const val MAX_PER_QR = 10

    private const val VERSION = 1
    private const val TYPE_HOTP = 1
    private const val TYPE_TOTP = 2

    enum class SkipReason { PERIOD, STEAM, OTHER }

    data class Skipped(val account: VaultAccount, val reason: SkipReason)

    data class Result(
        val uris: List<String>,
        val exported: List<VaultAccount>,
        val skipped: List<Skipped>
    ) {
        val isEmpty: Boolean get() = exported.isEmpty()
    }

    /**
     * Splits [accounts] into batches of [MAX_PER_QR] and returns one migration
     * URI per batch. [batchId] only has to be identical across the batches of
     * one export; it exists so a paged scan keeps its pages together.
     */
    fun build(accounts: List<VaultAccount>, batchId: Int = 0): Result {
        val exported = ArrayList<VaultAccount>(accounts.size)
        val skipped = ArrayList<Skipped>()

        for (account in accounts) {
            when {
                account.issuer.equals("Steam", ignoreCase = true) ->
                    skipped += Skipped(account, SkipReason.STEAM)
                account.period != 30 && !account.isHotp ->
                    skipped += Skipped(account, SkipReason.PERIOD)
                account.algorithm.uppercase() !in SUPPORTED_ALGORITHMS ->
                    skipped += Skipped(account, SkipReason.OTHER)
                account.digits != 6 && account.digits != 8 ->
                    skipped += Skipped(account, SkipReason.OTHER)
                !account.isTotp && !account.isHotp ->
                    skipped += Skipped(account, SkipReason.OTHER)
                else -> {
                    val secret = runCatching { Base32.decode(account.secret) }.getOrNull()
                    if (secret == null || secret.isEmpty()) {
                        skipped += Skipped(account, SkipReason.OTHER)
                    } else {
                        exported += account
                    }
                }
            }
        }

        val batches = exported.chunked(MAX_PER_QR)
        val uris = batches.mapIndexed { index, batch ->
            uri(
                accounts = batch,
                batchSize = batches.size,
                batchIndex = index,
                batchId = batchId
            )
        }
        return Result(uris, exported, skipped)
    }

    private val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512")

    private val VaultAccount.isHotp: Boolean get() = type.equals("hotp", ignoreCase = true)
    private val VaultAccount.isTotp: Boolean get() = type.equals("totp", ignoreCase = true)

    private fun uri(
        accounts: List<VaultAccount>,
        batchSize: Int,
        batchIndex: Int,
        batchId: Int
    ): String {
        val payload = payload(accounts, batchSize, batchIndex, batchId)
        val data = URLEncoder.encode(
            Base64.getEncoder().encodeToString(payload),
            "UTF-8"
        )
        return "otpauth-migration://offline?data=$data"
    }

    private fun payload(
        accounts: List<VaultAccount>,
        batchSize: Int,
        batchIndex: Int,
        batchId: Int
    ): ByteArray {
        val out = ArrayList<Byte>(256)
        for (account in accounts) {
            val params = otpParameters(account)
            out += tag(1, WIRE_LENGTH_DELIMITED)
            out += varint(params.size.toLong())
            out += params.toList()
        }
        out += tag(2, WIRE_VARINT); out += varint(VERSION.toLong())
        out += tag(3, WIRE_VARINT); out += varint(batchSize.toLong())
        out += tag(4, WIRE_VARINT); out += varint(batchIndex.toLong())
        out += tag(5, WIRE_VARINT); out += varint(batchId.toLong())
        return out.toByteArray()
    }

    private fun otpParameters(account: VaultAccount): ByteArray {
        val out = ArrayList<Byte>(64)
        val secret = Base32.decode(account.secret)
        out += tag(1, WIRE_LENGTH_DELIMITED); out += varint(secret.size.toLong()); out += secret.toList()

        out += tag(2, WIRE_LENGTH_DELIMITED); out += stringBytes(account.label)
        out += tag(3, WIRE_LENGTH_DELIMITED); out += stringBytes(account.issuer)

        out += tag(4, WIRE_VARINT)
        out += varint(algorithmCode(account.algorithm).toLong())

        out += tag(5, WIRE_VARINT)
        out += varint(if (account.digits == 8) 2L else 1L)

        out += tag(6, WIRE_VARINT)
        out += varint((if (account.isHotp) TYPE_HOTP else TYPE_TOTP).toLong())

        if (account.isHotp) {
            out += tag(7, WIRE_VARINT); out += varint(account.counter)
        }
        return out.toByteArray()
    }

    private fun algorithmCode(algorithm: String): Int = when (algorithm.uppercase()) {
        "SHA256" -> 2
        "SHA512" -> 3
        "MD5" -> 4
        else -> 1
    }

    private fun stringBytes(value: String): List<Byte> {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return varint(bytes.size.toLong()) + bytes.toList()
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_LENGTH_DELIMITED = 2

    private fun tag(field: Int, wireType: Int): List<Byte> =
        varint(((field shl 3) or wireType).toLong())

    private fun varint(value: Long): List<Byte> {
        var v = value
        val out = ArrayList<Byte>(10)
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out += v.toByte()
                return out
            }
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
    }
}

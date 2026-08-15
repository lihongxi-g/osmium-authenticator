package com.safekey.authenticator.totp

import java.net.URLDecoder
import java.util.Base64

/**
 * Parser for Google Authenticator's "Transfer accounts" payload.
 *
 * Google's export QR codes use a proprietary (but stable, unencrypted) format:
 *   otpauth-migration://offline?data=<base64 protobuf MigrationPayload>
 *
 * The protobuf schema is the community-standard one also used by Aegis:
 *   message MigrationPayload {
 *     repeated OtpParameters otp_parameters = 1;
 *     int32 version = 2; int32 batch_size = 3;
 *     optional int32 batch_index = 4; int32 batch_id = 5;
 *   }
 *   message OtpParameters {
 *     bytes secret = 1; string name = 2; string issuer = 3;
 *     Algorithm algorithm = 4;  // 1=SHA1 2=SHA256 3=SHA512 4=MD5
 *     DigitCount digits = 5;    // 1=6 digits 2=8 digits
 *     OtpType type = 6;         // 1=HOTP 2=TOTP
 *     int64 counter = 7;
 *   }
 *
 * Implemented as a hand-rolled protobuf wire-format reader — no protobuf
 * dependency, ~100 lines, zero reflection.
 */
object GoogleMigrationParser {

    const val SCHEME_PREFIX = "otpauth-migration://offline"

    data class MigrationAccount(
        val secret: String,
        val name: String,
        val issuer: String,
        val algorithm: String,
        val digits: Int,
        val type: String,
        val counter: Long
    ) {
        /** MD5-based or secret-less accounts cannot be reproduced by standard TOTP. */
        val isUnsupported: Boolean get() = algorithm == "MD5" || secret.isEmpty()
    }

    /** True when the raw QR payload is a Google migration URI. */
    fun isMigrationUri(raw: String): Boolean =
        raw.trim().startsWith(SCHEME_PREFIX, ignoreCase = true)

    /**
     * Parses a full otpauth-migration:// URI into its accounts.
     * @throws IllegalArgumentException on malformed payloads.
     */
    fun parse(rawUri: String): List<MigrationAccount> {
        val trimmed = rawUri.trim()
        val dataRaw = trimmed.substringAfter("data=", "")
            .substringBefore("&").trim()
        if (dataRaw.isEmpty()) {
            throw IllegalArgumentException("Missing data parameter")
        }
        // Google may percent-encode the base64 payload inside the QR URI
        // (%2B %2F %3D). Decode those escapes before base64.
        val dataParam = percentDecode(dataRaw)
        val bytes = try {
            decodeBase64(dataParam)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid base64 payload", e)
        }
        val payload = try {
            parsePayload(bytes)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported migration payload", e)
        }
        if (payload.isEmpty()) {
            throw IllegalArgumentException("No accounts found in payload")
        }
        return payload
    }

    private fun percentDecode(s: String): String {
        if (!s.contains("%")) return s
        // URLDecoder treats '+' as space (form semantics); protect base64 '+'
        // by re-escaping it first, then decode the remaining percent escapes.
        return try {
            URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }
    }

    private fun decodeBase64(data: String): ByteArray {
        // Google QR payloads may use standard or URL-safe base64, padded or not
        val normalized = data.replace('-', '+').replace('_', '/')
        return try {
            Base64.getDecoder().decode(normalized)
        } catch (_: Exception) {
            // retry with explicit padding
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            Base64.getDecoder().decode(padded)
        }
    }

    private fun parsePayload(bytes: ByteArray): List<MigrationAccount> {
        val out = mutableListOf<MigrationAccount>()
        var i = 0
        while (i < bytes.size) {
            val tag = readVarint(bytes, i)
            i = tag.second
            val fieldNumber = (tag.first ushr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()
            when {
                fieldNumber == 1 && wireType == 2 -> {
                    val len = readVarint(bytes, i)
                    i = len.second
                    val end = i + len.first.toInt()
                    out.add(parseOtpParameters(bytes, i, end))
                    i = end
                }
                wireType == 0 -> {
                    val v = readVarint(bytes, i)
                    i = v.second // version/batch fields: skip value
                }
                wireType == 2 -> {
                    val len = readVarint(bytes, i)
                    i = len.second + len.first.toInt()
                }
                wireType == 5 -> i += 4 // fixed32, unused here
                wireType == 1 -> i += 8 // fixed64, unused here
                else -> throw IllegalArgumentException("Unsupported wire type $wireType")
            }
        }
        return out
    }

    private fun parseOtpParameters(bytes: ByteArray, start: Int, end: Int): MigrationAccount {
        var secret = ""
        var name = ""
        var issuer = ""
        var algorithm = 0
        var digits = 0
        var type = 0
        var counter = 0L
        var i = start
        while (i < end) {
            val tag = readVarint(bytes, i)
            i = tag.second
            val fieldNumber = (tag.first ushr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()
            when (fieldNumber) {
                1 -> { // bytes secret
                    val len = readVarint(bytes, i)
                    secret = String(bytes, len.second, len.first.toInt(), Charsets.US_ASCII)
                    i = len.second + len.first.toInt()
                }
                2 -> { // string name
                    val len = readVarint(bytes, i)
                    name = String(bytes, len.second, len.first.toInt(), Charsets.UTF_8)
                    i = len.second + len.first.toInt()
                }
                3 -> { // string issuer
                    val len = readVarint(bytes, i)
                    issuer = String(bytes, len.second, len.first.toInt(), Charsets.UTF_8)
                    i = len.second + len.first.toInt()
                }
                4 -> { // enum algorithm
                    val v = readVarint(bytes, i)
                    algorithm = v.first.toInt()
                    i = v.second
                }
                5 -> { // enum digits
                    val v = readVarint(bytes, i)
                    digits = v.first.toInt()
                    i = v.second
                }
                6 -> { // enum type
                    val v = readVarint(bytes, i)
                    type = v.first.toInt()
                    i = v.second
                }
                7 -> { // int64 counter
                    val v = readVarint(bytes, i)
                    counter = v.first
                    i = v.second
                }
                else -> {
                    // skip unknown field
                    if (wireType == 0) {
                        val v = readVarint(bytes, i)
                        i = v.second
                    } else if (wireType == 2) {
                        val len = readVarint(bytes, i)
                        i = len.second + len.first.toInt()
                    } else {
                        i = end
                    }
                }
            }
        }
        return MigrationAccount(
            secret = normalizedSecret(secret.trim()),
            name = name,
            issuer = issuer,
            algorithm = when (algorithm) {
                1 -> "SHA1"
                2 -> "SHA256"
                3 -> "SHA512"
                4 -> "MD5"
                else -> "MD5"
            },
            digits = if (digits == 2) 8 else 6,
            type = if (type == 1) "hotp" else "totp",
            counter = counter
        )
    }

    /** Canonicalize the secret like OtpUriParser does (decode → re-encode,
     *  padding stripped). Google stores most secrets as Base32 text, but some
     *  accounts use Base64 — try Base32 first, then Base64. Invalid secrets
     *  return "" and the account is marked unsupported. */
    private fun normalizedSecret(raw: String): String {
        if (raw.isEmpty()) return ""
        val asB32 = try {
            val bytes = Base32.decode(raw)
            if (bytes.size < 10) null else bytes
        } catch (_: Exception) {
            null
        }
        if (asB32 != null) return Base32.encode(asB32).replace("=", "")
        val asB64 = try {
            val bytes = Base64.getDecoder().decode(raw)
            if (bytes.size < 10) null else bytes
        } catch (_: Exception) {
            null
        }
        return if (asB64 != null) Base32.encode(asB64).replace("=", "") else ""
    }

    /** Reads an unsigned LEB128 varint; returns (value, nextIndex). */
    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            i++
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 63) throw IllegalArgumentException("Varint too long")
        }
        return result to i
    }
}

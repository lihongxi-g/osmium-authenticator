package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One blocked peer device. Identity fields are filled with whatever the app
 * could observe; an empty field never matches anything (a block must never
 * become a wildcard).
 *
 * IP alone is weak (DHCP reassigns addresses) and a MAC alone is not always
 * observable, so a device is identified by the union of the signals the user
 * asked for: current LAN address, the peer's hardware address when it is in
 * this device's ARP table, and the peer's self-reported install id.
 */
@Serializable
data class LanBlock(
    val ip: String = "",
    val mac: String = "",
    val deviceId: String = "",
    val label: String = "",
    val blockedAt: Long = 0L,
    val expiresAt: Long = 0L
)

/**
 * Pure rules for the 24-hour transfer block list — unit-tested on the JVM
 * (LanBlockRulesTest).
 */
internal object LanBlockRules {

    /** How long a block stays effective. */
    const val WINDOW_MS = 24L * 60L * 60L * 1000L

    /** Blocks that have not expired yet. */
    fun active(blocks: List<LanBlock>, now: Long): List<LanBlock> =
        blocks.filter { it.expiresAt > now }

    /**
     * True when [block] names the candidate. Comparison is case-insensitive and
     * every field is optional, but a blank field in the block never matches.
     */
    fun matches(block: LanBlock, ip: String?, mac: String?, deviceId: String?): Boolean {
        if (block.ip.isNotBlank() && ip != null && block.ip == ip) return true
        if (block.mac.isNotBlank() && mac != null &&
            block.mac.equals(mac, ignoreCase = true)
        ) {
            return true
        }
        if (block.deviceId.isNotBlank() && deviceId != null && block.deviceId == deviceId) return true
        return false
    }

    /** The first active block naming the candidate, or null. */
    fun find(
        blocks: List<LanBlock>,
        now: Long,
        ip: String?,
        mac: String?,
        deviceId: String?
    ): LanBlock? = active(blocks, now).firstOrNull { matches(it, ip, mac, deviceId) }

    /**
     * Adds [block] (24h by default), replacing any existing entry for the same
     * device, and drops expired entries on the way.
     */
    fun upsert(blocks: List<LanBlock>, block: LanBlock, now: Long): List<LanBlock> {
        val kept = active(blocks, now).filterNot {
            matches(it, block.ip.ifBlank { null }, block.mac.ifBlank { null },
                block.deviceId.ifBlank { null })
        }
        return kept + block
    }

    /** Removes every entry naming the candidate. */
    fun remove(
        blocks: List<LanBlock>,
        ip: String?,
        mac: String?,
        deviceId: String?
    ): List<LanBlock> = blocks.filterNot { matches(it, ip, mac, deviceId) }
}

private val Context.lanGuardStore by preferencesDataStore(name = "safekey_lan_guard")

/**
 * Encrypted 24-hour block list for LAN transfers. The stored blob is encrypted
 * with the AndroidKeyStore master key (same mechanism as the PIN and WebDAV
 * passwords), so the list is not readable from a data backup or by another app
 * even on a rooted device.
 */
class LanBlockRepository(
    private val context: Context,
    private val crypto: CryptoManager
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class LanBlockFile(val blocks: List<LanBlock> = emptyList())

    /** Active blocks, newest first; expired entries are filtered on read. */
    val blocks: Flow<List<LanBlock>> = context.lanGuardStore.data.map { prefs ->
        val raw = prefs[KEY_BLOCKS] ?: return@map emptyList()
        decode(raw)
            .let { LanBlockRules.active(it, System.currentTimeMillis()) }
            .sortedByDescending { it.blockedAt }
    }

    suspend fun block(
        ip: String?,
        mac: String?,
        deviceId: String?,
        label: String,
        now: Long = System.currentTimeMillis()
    ): LanBlock {
        val entry = LanBlock(
            ip = ip.orEmpty(),
            mac = mac.orEmpty(),
            deviceId = deviceId.orEmpty(),
            label = label,
            blockedAt = now,
            expiresAt = now + LanBlockRules.WINDOW_MS
        )
        context.lanGuardStore.edit { prefs ->
            val current = decode(prefs[KEY_BLOCKS] ?: "")
            prefs[KEY_BLOCKS] = encode(LanBlockRules.upsert(current, entry, now))
        }
        return entry
    }

    suspend fun unblock(ip: String?, mac: String?, deviceId: String?) {
        context.lanGuardStore.edit { prefs ->
            val current = decode(prefs[KEY_BLOCKS] ?: "")
            prefs[KEY_BLOCKS] = encode(LanBlockRules.remove(current, ip, mac, deviceId))
        }
    }

    suspend fun clear() {
        context.lanGuardStore.edit { it.clear() }
    }

    /** Block entry matching a peer, if any (used by the transfer handshake). */
    suspend fun findActive(ip: String?, mac: String?, deviceId: String?): LanBlock? =
        LanBlockRules.find(
            decode(context.lanGuardStore.data.first()[KEY_BLOCKS] ?: ""),
            System.currentTimeMillis(),
            ip, mac, deviceId
        )

    // ------------------------------------------------------------- encoding

    private fun encode(blocks: List<LanBlock>): String {
        val payload = json.encodeToString(LanBlockFile(blocks))
        val field = crypto.encrypt(payload)
        return "${field.iv}.${field.ciphertext}"
    }

    private fun decode(raw: String): List<LanBlock> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.split(".", limit = 2)
        if (parts.size != 2) return emptyList()
        return try {
            val plain = crypto.decrypt(CryptoManager.EncryptedField(parts[0], parts[1]))
            json.decodeFromString<LanBlockFile>(plain).blocks
        } catch (e: Exception) {
            // A corrupt or re-keyed blob must never break the transfer screen.
            AppLog.d("lan block list unreadable: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private companion object {
        val KEY_BLOCKS = stringPreferencesKey("block_list")
    }
}

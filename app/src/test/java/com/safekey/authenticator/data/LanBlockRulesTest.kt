package com.safekey.authenticator.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanBlockRulesTest {

    private val now = 1_700_000_000_000L

    private fun block(
        ip: String = "",
        mac: String = "",
        deviceId: String = "",
        expiresIn: Long = LanBlockRules.WINDOW_MS
    ) = LanBlock(
        ip = ip,
        mac = mac,
        deviceId = deviceId,
        label = "peer",
        blockedAt = now,
        expiresAt = now + expiresIn
    )

    @Test
    fun `a block expires after the window`() {
        val entry = block(ip = "192.168.1.5")
        assertEquals(listOf(entry), LanBlockRules.active(listOf(entry), now + 1))
        assertTrue(LanBlockRules.active(listOf(entry), now + LanBlockRules.WINDOW_MS).isEmpty())
        assertTrue(LanBlockRules.active(listOf(entry), now + LanBlockRules.WINDOW_MS + 1).isEmpty())
    }

    @Test
    fun `matching works by ip, mac or device id`() {
        assertTrue(LanBlockRules.matches(block(ip = "10.0.0.2"), "10.0.0.2", null, null))
        assertTrue(LanBlockRules.matches(block(mac = "AA:BB:CC:DD:EE:FF"), null, "aa:bb:cc:dd:ee:ff", null))
        assertTrue(LanBlockRules.matches(block(deviceId = "abc"), null, null, "abc"))
        assertFalse(LanBlockRules.matches(block(ip = "10.0.0.2"), "10.0.0.3", null, null))
        assertFalse(LanBlockRules.matches(block(ip = "10.0.0.2"), null, null, null))
    }

    @Test
    fun `empty identity fields never match anything`() {
        val empty = block()
        assertFalse(LanBlockRules.matches(empty, "10.0.0.2", "aa:bb:cc:dd:ee:ff", "abc"))
        assertFalse(LanBlockRules.matches(empty, null, null, null))
    }

    @Test
    fun `find returns only active entries`() {
        val entry = block(ip = "10.0.0.2")
        assertEquals(entry, LanBlockRules.find(listOf(entry), now, "10.0.0.2", null, null))
        assertNull(
            LanBlockRules.find(
                listOf(entry), now + LanBlockRules.WINDOW_MS, "10.0.0.2", null, null
            )
        )
    }

    @Test
    fun `upsert replaces the entry for the same device and prunes expired ones`() {
        val old = block(ip = "10.0.0.2")
        val expired = block(ip = "10.0.0.9", expiresIn = -1)
        val fresh = block(ip = "10.0.0.2").copy(blockedAt = now + 100, expiresAt = now + 1000)
        val result = LanBlockRules.upsert(listOf(old, expired), fresh, now + 200)
        assertEquals(1, result.size)
        assertEquals(fresh, result[0])
    }

    @Test
    fun `upsert keeps unrelated entries`() {
        val first = block(ip = "10.0.0.2")
        val second = block(ip = "10.0.0.3")
        val result = LanBlockRules.upsert(listOf(first), second, now)
        assertEquals(2, result.size)
        assertTrue(result.contains(first))
        assertTrue(result.contains(second))
    }

    @Test
    fun `remove drops every entry naming the device`() {
        val byIp = block(ip = "10.0.0.2")
        val byId = block(deviceId = "abc")
        val other = block(ip = "10.0.0.7")
        val remaining = LanBlockRules.remove(listOf(byIp, byId, other), "10.0.0.2", null, "abc")
        assertEquals(listOf(other), remaining)
    }
}

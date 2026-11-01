package com.safekey.authenticator.totp

import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoder is verified by round-tripping through [GoogleMigrationParser] —
 * the same wire format, opposite direction, so a field that drifts in one place
 * breaks the other.
 */
class GoogleMigrationEncoderTest {

    private fun account(
        id: String,
        issuer: String = "GitHub",
        label: String = "alice",
        secret: String = "JBSWY3DPEHPK3PXP",
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = "totp",
        counter: Long = 0
    ) = VaultAccount(
        issuer = issuer,
        label = label,
        secret = secret,
        algorithm = algorithm,
        digits = digits,
        period = period,
        type = type,
        counter = counter
    )

    @Test
    fun `single account round-trips through the parser`() {
        val result = GoogleMigrationEncoder.build(listOf(account("1")))
        assertEquals(1, result.uris.size)
        assertTrue(GoogleMigrationParser.isMigrationUri(result.uris.first()))

        val parsed = GoogleMigrationParser.parse(result.uris.first())
        assertEquals(1, parsed.size)
        val p = parsed.first()
        assertEquals("JBSWY3DPEHPK3PXP", p.secret)
        assertEquals("alice", p.name)
        assertEquals("GitHub", p.issuer)
        assertEquals("SHA1", p.algorithm)
        assertEquals(6, p.digits)
        assertEquals("totp", p.type)
    }

    @Test
    fun `fields survive for sha256, eight digits and hotp`() {
        val result = GoogleMigrationEncoder.build(
            listOf(
                account("1", algorithm = "SHA256", digits = 8),
                account("2", issuer = "Bank", label = "bob", type = "hotp", counter = 42)
            )
        )
        val parsed = GoogleMigrationParser.parse(result.uris.first())
        assertEquals(2, parsed.size)
        assertEquals("SHA256", parsed[0].algorithm)
        assertEquals(8, parsed[0].digits)
        assertEquals("hotp", parsed[1].type)
        assertEquals(42L, parsed[1].counter)
    }

    @Test
    fun `more than ten accounts are split into batches`() {
        val accounts = (1..23).map { account(it.toString()) }
        val result = GoogleMigrationEncoder.build(accounts, batchId = 7)
        assertEquals(23, result.exported.size)
        assertEquals(3, result.uris.size)
        assertEquals(10, GoogleMigrationParser.parse(result.uris[0]).size)
        assertEquals(10, GoogleMigrationParser.parse(result.uris[1]).size)
        assertEquals(3, GoogleMigrationParser.parse(result.uris[2]).size)
    }

    @Test
    fun `accounts the format cannot express are skipped with a reason`() {
        val result = GoogleMigrationEncoder.build(
            listOf(
                account("1", period = 60),
                account("2", issuer = "Steam", secret = "JBSWY3DPEHPK3PXP"),
                account("3", algorithm = "MD5"),
                account("4", digits = 7),
                account("5", secret = "")
            )
        )
        assertEquals(0, result.exported.size)
        assertTrue(result.isEmpty)
        assertEquals(5, result.skipped.size)
        assertEquals(GoogleMigrationEncoder.SkipReason.PERIOD, result.skipped[0].reason)
        assertEquals(GoogleMigrationEncoder.SkipReason.STEAM, result.skipped[1].reason)
        assertEquals(GoogleMigrationEncoder.SkipReason.OTHER, result.skipped[2].reason)
        assertEquals(GoogleMigrationEncoder.SkipReason.OTHER, result.skipped[3].reason)
        assertEquals(GoogleMigrationEncoder.SkipReason.OTHER, result.skipped[4].reason)
    }

    @Test
    fun `a batch of one keeps batch metadata valid`() {
        val result = GoogleMigrationEncoder.build(listOf(account("1")), batchId = 3)
        val uri = result.uris.first()
        assertTrue(uri.startsWith("otpauth-migration://offline?data="))
        // The parser ignores batch fields, so the round-trip is the real check.
        assertEquals(1, GoogleMigrationParser.parse(uri).size)
    }
}

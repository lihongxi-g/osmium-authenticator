package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImportSupportTest {

    private fun vault(
        issuer: String = "GitHub",
        label: String = "octocat",
        secret: String = "JBSWY3DPEHPK3PXP",
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = Account.TYPE_TOTP,
        counter: Long = 0
    ) = VaultAccount(issuer, label, secret, algorithm, digits, period, type, counter)

    @Test
    fun `plain totp and hotp entries are importable`() {
        assertNull(ImportSupport.issue(vault()))
        assertNull(ImportSupport.issue(
            vault(secret = "5OM4WOOGPLQEF6UGN3CPEOOLWU", algorithm = "SHA256", digits = 8)
        ))
        assertNull(ImportSupport.issue(
            vault(algorithm = "SHA512", type = Account.TYPE_HOTP, counter = 10300)
        ))
        // lowercase secret still decodes (Base32 is case-insensitive)
        assertNull(ImportSupport.issue(vault(secret = "jbswy3dpehpk3pxp")))
    }

    @Test
    fun `steam entries are importable regardless of digits`() {
        assertNull(ImportSupport.issue(
            vault(issuer = "Steam", label = "Sophia", secret = "JRZCL47CMXVOQMNPZR2F7J4RGI", digits = 5)
        ))
        assertNull(ImportSupport.issue(
            vault(issuer = "steam", digits = 5)
        ))
    }

    @Test
    fun `unknown types are rejected`() {
        assertEquals(EntryIssue.UNSUPPORTED_TYPE, ImportSupport.issue(vault(type = "motp")))
        assertEquals(EntryIssue.UNSUPPORTED_TYPE, ImportSupport.issue(vault(type = "hotp_extra")))
    }

    @Test
    fun `unsupported algorithms are rejected`() {
        assertEquals(EntryIssue.UNSUPPORTED_ALGORITHM, ImportSupport.issue(vault(algorithm = "MD5")))
        assertEquals(EntryIssue.UNSUPPORTED_ALGORITHM, ImportSupport.issue(vault(algorithm = "sha-1")))
    }

    @Test
    fun `non 6 or 8 digit totp is rejected but steam is exempt`() {
        assertEquals(EntryIssue.UNSUPPORTED_DIGITS, ImportSupport.issue(vault(digits = 7)))
        assertEquals(EntryIssue.UNSUPPORTED_DIGITS, ImportSupport.issue(vault(digits = 5)))
        assertEquals(EntryIssue.UNSUPPORTED_DIGITS, ImportSupport.issue(vault(digits = 0)))
        assertNull(ImportSupport.issue(vault(issuer = "Steam", digits = 5)))
    }

    @Test
    fun `blank or malformed secrets are rejected`() {
        assertEquals(EntryIssue.INVALID_SECRET, ImportSupport.issue(vault(secret = "")))
        assertEquals(EntryIssue.INVALID_SECRET, ImportSupport.issue(vault(secret = "NOT BASE32!!")))
        // 4 bytes only — below the 10-byte floor
        assertEquals(EntryIssue.INVALID_SECRET, ImportSupport.issue(vault(secret = "MZXW6")))
        // valid but exactly at the floor
        assertNull(ImportSupport.issue(vault(secret = "JBSWY3DPEHPK3PXP"))) // 10 bytes
    }

    @Test
    fun `type mapping handles steam and unknown tokens`() {
        assertEquals(TypeMapping(Account.TYPE_TOTP, false), mapEntryType("totp"))
        assertEquals(TypeMapping(Account.TYPE_TOTP, false), mapEntryType("TOTP"))
        assertEquals(TypeMapping(Account.TYPE_HOTP, false), mapEntryType("Hotp"))
        assertEquals(TypeMapping(Account.TYPE_TOTP, true), mapEntryType("steam"))
        assertEquals(TypeMapping(Account.TYPE_TOTP, true), mapEntryType("STEAM"))
        assertEquals(TypeMapping(Account.TYPE_TOTP, false), mapEntryType(null))
        assertEquals(TypeMapping("motp", false), mapEntryType("MOTP"))
    }

    @Test
    fun `vaultRow applies defaults and steam issuer override`() {
        val row = vaultRow("Deno", "Mason", "4SJHB4GSD43FZBAI7C2HLRJGPQ======", "TOTP", null, null, null, null)
        assertNotNull(row)
        assertEquals("Deno", row!!.issuer)
        assertEquals("Mason", row.label)
        assertEquals("4SJHB4GSD43FZBAI7C2HLRJGPQ", row.secret)
        assertEquals("SHA1", row.algorithm)
        assertEquals(6, row.digits)
        assertEquals(30, row.period)
        assertEquals(Account.TYPE_TOTP, row.type)

        val steam = vaultRow("Boeing", "Sophia", "JRZCL47CMXVOQMNPZR2F7J4RGI", "steam", "SHA1", 5, 30, null)
        assertEquals("Steam", steam!!.issuer)
        assertEquals("Sophia", steam.label)
        assertEquals(Account.TYPE_TOTP, steam.type)

        val hotp = vaultRow("Issuu", "James", "YOOMIXWS5GN6RTBPUFFWKTW5M4", "HOTP", "SHA1", 6, null, 1)
        assertEquals(Account.TYPE_HOTP, hotp!!.type)
        assertEquals(1L, hotp.counter)
        assertEquals(30, hotp.period)
    }

    @Test
    fun `vaultRow drops entries with no name at all`() {
        assertNull(vaultRow(null, null, "JBSWY3DPEHPK3PXP", "totp", null, null, null, null))
        assertNull(vaultRow("  ", "", "JBSWY3DPEHPK3PXP", "totp", null, null, null, null))
    }

    @Test
    fun `normalizeSecret strips padding whitespace and dashes`() {
        assertEquals("4SJHB4GSD43FZBAI7C2HLRJGPQ", normalizeSecret("4SJHB4GSD43FZBAI7C2HLRJGPQ======"))
        assertEquals("JBSWY3DPEHPK3PXP", normalizeSecret(" jbswy3dpehpk3pxp"))
        assertEquals("JBSWY3DPEHPK3PXP", normalizeSecret("JBSW-Y3DP-EHPK-3PXP"))
        assertEquals("", normalizeSecret(null))
        assertEquals("", normalizeSecret(""))
    }
}

package com.safekey.authenticator.totp.exporter

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.importer.AegisImporter
import com.safekey.authenticator.totp.importer.CsvOtpImporter
import com.safekey.authenticator.totp.importer.ImporterError
import com.safekey.authenticator.totp.importer.ImporterException
import com.safekey.authenticator.totp.importer.OtpUriListImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration targets must be readable by the apps they name, so every
 * exporter is verified by feeding its own output back through the matching
 * importer (which was itself written against those apps' real formats).
 */
class ExportersTest {

    private val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    private val totp = VaultAccount(
        issuer = "GitHub", label = "octocat", secret = secret,
        algorithm = "SHA1", digits = 6, period = 30
    )
    private val hotp = VaultAccount(
        issuer = "Example", label = "user", secret = secret,
        algorithm = "SHA256", digits = 8, period = 30,
        type = Account.TYPE_HOTP, counter = 42
    )
    private val steam = VaultAccount(
        issuer = "Steam", label = "account", secret = secret,
        algorithm = "SHA1", digits = 6, period = 30
    )
    private val awkward = VaultAccount(
        issuer = "我的 服务, 公司", label = "user+tag & more", secret = secret,
        algorithm = "SHA512", digits = 6, period = 60
    )

    @Test
    fun `otpauth links round-trip through the link importer`() {
        val accounts = listOf(totp, hotp, steam, awkward)
        val text = Exporters.build(ExportFormat.OTP_LINKS, accounts)
        assertEquals(4, text.lines().size)

        val rows = OtpUriListImporter.parse(text)
        assertEquals(4, rows.size)
        assertEquals(totp, rows[0])
        // HOTP keeps its counter and never gains a period
        assertEquals(hotp, rows[1])
        assertEquals("Steam", rows[2].issuer)
        assertEquals(secret, rows[2].secret)
        // spaces, commas, CJK and ampersands survive the encoding
        assertEquals(awkward.issuer, rows[3].issuer)
        assertEquals(awkward.label, rows[3].label)
        assertEquals(60, rows[3].period)
        assertEquals("SHA512", rows[3].algorithm)
    }

    @Test
    fun `otpauth links keep the label colon and encode everything else`() {
        val link = totp.toOtpLink()
        assertTrue(link.startsWith("otpauth://totp/GitHub:octocat?"))
        assertTrue(link.contains("secret=$secret"))
        assertTrue(link.contains("issuer=GitHub"))
        assertTrue(link.contains("period=30"))
        // HOTP links use counter, not period
        val hotpLink = hotp.toOtpLink()
        assertTrue(hotpLink.contains("&counter=42"))
        assertTrue(!hotpLink.contains("period="))
    }

    @Test
    fun `percent encoding follows rfc 3986`() {
        assertEquals("A%20B", encodeComponent("A B"))
        assertEquals("user%2Btag%20%26%20more", encodeComponent("user+tag & more"))
        assertEquals("a-b._~", encodeComponent("a-b._~"))
        // CJK becomes three UTF-8 %XX escapes per character
        assertEquals("%E6%88%91", encodeComponent("我"))
    }

    @Test
    fun `aegis json round-trips through the aegis importer`() {
        val json = Exporters.build(ExportFormat.AEGIS_JSON, listOf(totp, hotp, steam))
        assertTrue(json.contains("\"db\""))
        assertTrue(json.contains("\"entries\""))

        val rows = AegisImporter.parse(json)
        assertEquals(3, rows.size)
        assertEquals(totp, rows[0])
        assertEquals(hotp, rows[1])
        // Aegis stores Steam with its fixed 5 digits
        assertEquals("Steam", rows[2].issuer)
        assertEquals(5, rows[2].digits)
        assertEquals(secret, rows[2].secret)
    }

    @Test
    fun `aegis json uses a stable uuid so re-export is recognised`() {
        val first = Exporters.build(ExportFormat.AEGIS_JSON, listOf(totp))
        val second = Exporters.build(ExportFormat.AEGIS_JSON, listOf(totp))
        assertEquals(first, second)
        // uuid must be a real UUID (Aegis rejects anything else)
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue(uuid.containsMatchIn(first))
    }

    @Test
    fun `keePassxc csv round-trips through the csv importer`() {
        val csv = Exporters.build(ExportFormat.KEEPASS_CSV, listOf(totp, awkward))
        assertTrue(csv.startsWith("Group,Title,Username,Password,URL,Notes,TOTP,Icon,Last Modified,Created"))

        val rows = CsvOtpImporter.parse(csv)
        assertEquals(2, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals(secret, rows[0].secret)
        // a comma inside the issuer must be quoted, not split into a new column
        assertEquals(awkward.issuer, rows[1].issuer)
        assertEquals(awkward.label, rows[1].label)
    }

    @Test
    fun `an empty vault renders an empty document per format`() {
        // The screen treats a blank payload as "nothing to export", so the link
        // format must stay empty while the structured formats stay valid.
        assertEquals("", Exporters.build(ExportFormat.OTP_LINKS, emptyList()).trim())
        val json = Exporters.build(ExportFormat.AEGIS_JSON, emptyList())
        // an empty Aegis vault must still be a valid, parseable Aegis file
        val emptyError = assertThrows(ImporterException::class.java) { AegisImporter.parse(json) }
        assertEquals(ImporterError.EMPTY, emptyError.error)
        assertEquals(1, Exporters.build(ExportFormat.KEEPASS_CSV, emptyList()).trim().lines().size)
    }

    @Test
    fun `format registry stays consistent`() {
        val ids = ExportFormat.values().map { it.id }
        assertEquals("format ids must be unique", ids.size, ids.toSet().size)
        val names = ExportFormat.values().map { it.fileName }
        assertEquals("file names must be unique", names.size, names.toSet().size)
        assertTrue(
            "every format needs a extension so the save dialog keeps it",
            names.all { it.substringAfterLast('.', "").isNotEmpty() }
        )
    }
}

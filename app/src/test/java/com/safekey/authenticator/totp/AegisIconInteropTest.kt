package com.safekey.authenticator.totp

import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.totp.exporter.AegisJsonExporter
import com.safekey.authenticator.totp.importer.AegisImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Icon interoperability with Aegis: Aegis stores the chosen icon inside the
 * vault entry (`icon` = Base64 bytes, `icon_mime`, `icon_hash`) and Osmium has
 * to read and write exactly that shape.
 */
class AegisIconInteropTest {

    private val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
    private val iconBase64 = Base64.getEncoder().encodeToString(iconBytes)

    private fun account(icon: String = iconBase64, mime: String = "image/png") = VaultAccount(
        issuer = "Google",
        label = "user@gmail.com",
        secret = "JBSWY3DPEHPK3PXP",
        algorithm = "SHA1",
        digits = 6,
        period = 30,
        icon = icon,
        iconMime = mime
    )

    private fun aegisJson(icon: String?, mime: String?) = """
        {"version":1,"header":{"slots":null,"params":null},"db":{"version":1,"entries":[
          {"type":"totp","uuid":"8b0a2b4a-7d0f-4b6c-9c31-0f0e3f6a1b2c",
           "name":"user@gmail.com","issuer":"Google","note":null,
           "icon":${icon?.let { "\"$it\"" } ?: "null"},
           "icon_mime":${mime?.let { "\"$it\"" } ?: "null"},
           "info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}}]}}
    """.trimIndent()

    @Test
    fun `export writes base64 bytes, mime and the Aegis hash`() {
        val json = AegisJsonExporter.build(listOf(account()))
        val entry = Json.parseToJsonElement(json).jsonObject
            .getValue("db").jsonObject
            .getValue("entries").jsonArray[0].jsonObject

        assertEquals(iconBase64, entry.getValue("icon").jsonPrimitive.content)
        assertEquals("image/png", entry.getValue("icon_mime").jsonPrimitive.content)

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("image/png".toByteArray(Charsets.UTF_8))
        val expected = digest.digest(iconBytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, entry.getValue("icon_hash").jsonPrimitive.content)
    }

    @Test
    fun `export keeps icon null when the account has no icon`() {
        val json = AegisJsonExporter.build(listOf(account(icon = "", mime = "")))
        val entry = Json.parseToJsonElement(json).jsonObject
            .getValue("db").jsonObject
            .getValue("entries").jsonArray[0].jsonObject
        assertEquals("null", entry.getValue("icon").jsonPrimitive.content)
        assertNull(entry["icon_mime"])
    }

    @Test
    fun `import reads the embedded icon`() {
        val rows = AegisImporter.parse(aegisJson(iconBase64, "image/png"))
        assertEquals(1, rows.size)
        assertEquals(iconBase64, rows[0].icon)
        assertEquals("image/png", rows[0].iconMime)
    }

    @Test
    fun `import tolerates a null icon`() {
        val rows = AegisImporter.parse(aegisJson(null, null))
        assertTrue(rows[0].icon.isEmpty())
        assertTrue(rows[0].iconMime.isEmpty())
    }

    @Test
    fun `import drops an unsupported mime type`() {
        val rows = AegisImporter.parse(aegisJson(iconBase64, "application/octet-stream"))
        assertTrue(rows[0].icon.isEmpty())
        assertTrue(rows[0].iconMime.isEmpty())
    }

    @Test
    fun `import drops malformed base64`() {
        val rows = AegisImporter.parse(aegisJson("!!!not-base64!!!", "image/png"))
        assertTrue(rows[0].icon.isEmpty())
    }

    @Test
    fun `export then import round-trips the icon`() {
        val json = AegisJsonExporter.build(listOf(account()))
        val rows = AegisImporter.parse(json)
        assertEquals(iconBase64, rows[0].icon)
        assertEquals("image/png", rows[0].iconMime)
        assertEquals("Google", rows[0].issuer)
    }

    @Test
    fun `exported entries keep the plaintext envelope shape`() {
        val json = AegisJsonExporter.build(listOf(account(icon = "", mime = "")))
        val root: JsonObject = Json.parseToJsonElement(json).jsonObject
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        assertTrue(root.getValue("db").jsonObject.containsKey("entries"))
    }
}

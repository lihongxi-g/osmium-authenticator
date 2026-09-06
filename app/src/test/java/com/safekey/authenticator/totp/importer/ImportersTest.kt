package com.safekey.authenticator.totp.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Detection matrix over the real format fixtures in
 * src/test/resources/com/safekey/authenticator/totp/importer/.
 * Every fixture must be detected by exactly one importer, and none of the
 * negative probes may be detected by anyone.
 */
class ImportersTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) {
            "missing fixture $name"
        }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    private val samples: Map<String, String> = mapOf(
        "aegis_plain.json" to "aegis",
        "aegis_encrypted.json" to "aegis", // still an Aegis file — parse rejects it later
        "andotp_plain.json" to "andotp",
        "2fas_v1.json" to "2fas",
        "2fas_v2.json" to "2fas",
        "2fas_v3.json" to "2fas",
        "2fas_v4.json" to "2fas",
        "2fas_v4_encrypted.json" to "2fas",
        "raivo_sample.json" to "raivo",
        "raivo_single_object.json" to "raivo",
        "lastpass_accounts.json" to "lastpass"
    )

    @Test
    fun `find returns the matching importer for every format fixture`() {
        samples.forEach { (file, expectedId) ->
            val content = fixture(file)
            assertEquals(
                "find($file) should resolve to $expectedId",
                expectedId,
                Importers.find(content)?.id
            )
        }
    }

    @Test
    fun `each fixture is detected by exactly one importer`() {
        samples.forEach { (file, expectedId) ->
            val content = fixture(file)
            Importers.all.forEach { importer ->
                val detected = importer.detect(content)
                if (importer.id == expectedId) {
                    assertEquals("$file must be detected by ${importer.id}", true, detected)
                } else {
                    assertEquals("$file must NOT be detected by ${importer.id}", false, detected)
                }
            }
        }
    }

    @Test
    fun `garbage and unrelated files are not detected`() {
        val probes = listOf(
            "",
            "   ",
            "hello world",
            "{\"name\":\"just some json\"}",
            "[1,2,3]",
            "<html><body>not a backup</body></html>",
            "otpauth-migration://offline?data=AAA",
            // 2FAS needs schemaVersion too
            """{"services":[]}""",
            // Aegis needs header too
            """{"db":{"entries":[]}}""",
            // andOTP needs a real JSON array of entry objects; this string
            // array carries no format markers at all
            """["one","two","three"]""",
            // A root array whose only object lacks the format markers
            """[{"foo":"bar"}]"""
        )
        probes.forEach { content ->
            assertNull("must not detect: ${content.take(60)}", Importers.find(content))
        }
    }

    @Test
    fun `empty-but-structurally-valid exports are still detected`() {
        // These look like real (empty) exports — detection must succeed so
        // the UI can show the "no accounts" message instead of "unknown file".
        assertEquals("lastpass", Importers.find("""{"accounts":[]}""")?.id)
        assertEquals("aegis", Importers.find("""{"version":1,"header":{"slots":null,"params":null},"db":{"version":1,"entries":[]}}""")?.id)
    }

    @Test
    fun `unrecognized JSON roots do not throw`() {
        assertNull(Importers.find("null"))
        assertNull(Importers.find("12345"))
        assertNull(Importers.find("\"a plain json string\""))
    }

    @Test
    fun `BOM and whitespace do not break detection`() {
        assertEquals("andotp", Importers.find("\uFEFF  " + fixture("andotp_plain.json"))?.id)
        assertEquals("2fas", Importers.find("\n\t" + fixture("2fas_v4.json"))?.id)
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}

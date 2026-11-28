package com.safekey.authenticator.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Security tests for icon pack extraction. The traversal cases mirror the
 * Aegis advisory GHSA-fw8c-jmjv-q6xf (CWE-22): a pack must never write
 * outside its own directory, whatever `pack.json` says.
 */
class IconPackStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var packsDir: File
    private lateinit var store: IconPackStore

    private fun setUp() {
        packsDir = File(tmp.root, "packs")
        store = IconPackStore(packsDir)
    }

    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><circle cx="5" cy="5" r="5"/></svg>"""
        .toByteArray()

    private fun zip(file: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    private fun definition(
        uuid: String = UUID.randomUUID().toString(),
        name: String = "Test pack",
        version: Int = 1,
        icons: List<Pair<String, String>> // filename to issuers (JSON fragment)
    ): ByteArray {
        val list = icons.joinToString(",") { (filename, issuer) ->
            """{"name":"$filename","filename":"$filename","category":null,"issuer":[$issuer]}"""
        }
        return """{"uuid":"$uuid","name":"$name","version":$version,"icons":[$list]}"""
            .toByteArray()
    }

    private fun validPack(
        version: Int = 1,
        uuid: String = UUID.randomUUID().toString(),
        extraEntries: List<Pair<String, ByteArray>> = emptyList(),
        icons: List<Pair<String, ByteArray>> = listOf("icons/a.svg" to svg, "icons/b.svg" to svg)
    ): File {
        val def = definition(
            uuid = uuid,
            version = version,
            icons = icons.map { (path, _) -> path to "\"${path.substringAfterLast('/').substringBefore('.')}\"" }
        )
        val file = File(tmp.root, "pack-${UUID.randomUUID()}.zip")
        zip(file, listOf("pack.json" to def) + icons + extraEntries)
        return file
    }

    @Test
    fun `valid pack installs and lists its icons`() {
        setUp()
        val installed = store.install(validPack())
        assertEquals(2, installed.iconCount)
        val listed = store.list()
        assertEquals(1, listed.size)
        assertEquals(2, listed[0].iconCount)
        assertTrue(listed[0].icons.all { it.file.isFile })
        assertTrue(File(packsDir, "${installed.uuid}/${installed.version}/pack.json").isFile)
    }

    @Test
    fun `parent traversal is rejected and nothing is written outside`() {
        setUp()
        val uuid = UUID.randomUUID().toString()
        val def = """{"uuid":"$uuid","name":"evil","version":1,"icons":[
            {"name":"evil","filename":"../evil.svg","category":null,"issuer":["evil"]}]}"""
        val file = File(tmp.root, "evil.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "../evil.svg" to svg))

        try {
            store.install(file)
            fail("traversal must be rejected")
        } catch (expected: IconPackException) {
            assertFalse(File(packsDir, "evil.svg").exists())
            assertFalse(File(tmp.root, "evil.svg").exists())
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `absolute paths are rejected`() {
        setUp()
        val def = """{"uuid":"${UUID.randomUUID()}","name":"evil","version":1,"icons":[
            {"name":"evil","filename":"/data/local/tmp/evil.svg","category":null,"issuer":["evil"]}]}"""
        val file = File(tmp.root, "abs.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "/data/local/tmp/evil.svg" to svg))
        try {
            store.install(file)
            fail("absolute path must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `backslash paths are rejected`() {
        setUp()
        val def = """{"uuid":"${UUID.randomUUID()}","name":"evil","version":1,"icons":[
            {"name":"evil","filename":"..\\\\evil.svg","category":null,"issuer":["evil"]}]}"""
        val file = File(tmp.root, "backslash.zip")
        zip(file, listOf("pack.json" to def.toByteArray()))
        try {
            store.install(file)
            fail("backslash path must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `unsupported extension is rejected`() {
        setUp()
        val def = """{"uuid":"${UUID.randomUUID()}","name":"bad","version":1,"icons":[
            {"name":"a","filename":"icons/a.txt","category":null,"issuer":["a"]}]}"""
        val file = File(tmp.root, "txt.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "icons/a.txt" to "hi".toByteArray()))
        try {
            store.install(file)
            fail("unsupported extension must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `missing icon file is rejected`() {
        setUp()
        val def = """{"uuid":"${UUID.randomUUID()}","name":"bad","version":1,"icons":[
            {"name":"a","filename":"icons/missing.svg","category":null,"issuer":["a"]}]}"""
        val file = File(tmp.root, "missing.zip")
        zip(file, listOf("pack.json" to def.toByteArray()))
        try {
            store.install(file)
            fail("missing file must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `invalid uuid is rejected`() {
        setUp()
        val def = """{"uuid":"../../etc","name":"bad","version":1,"icons":[
            {"name":"a","filename":"icons/a.svg","category":null,"issuer":["a"]}]}"""
        val file = File(tmp.root, "uuid.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "icons/a.svg" to svg))
        try {
            store.install(file)
            fail("invalid uuid must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `oversized pack json is rejected`() {
        setUp()
        val huge = ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() }
        val file = File(tmp.root, "huge.zip")
        zip(file, listOf("pack.json" to huge))
        try {
            store.install(file)
            fail("oversized pack.json must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `compression bomb is rejected`() {
        setUp()
        val zeros = ByteArray(900 * 1024)
        val uuid = UUID.randomUUID().toString()
        val def = """{"uuid":"$uuid","name":"bomb","version":1,"icons":[
            {"name":"a","filename":"icons/a.svg","category":null,"issuer":["a"]}]}"""
        val file = File(tmp.root, "bomb.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "icons/a.svg" to zeros))
        try {
            store.install(file)
            fail("compression bomb must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `svg with doctype is rejected`() {
        setUp()
        val evil = """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><text>&xxe;</text></svg>"""
            .toByteArray()
        val uuid = UUID.randomUUID().toString()
        val def = """{"uuid":"$uuid","name":"xxe","version":1,"icons":[
            {"name":"a","filename":"icons/a.svg","category":null,"issuer":["a"]}]}"""
        val file = File(tmp.root, "xxe.zip")
        zip(file, listOf("pack.json" to def.toByteArray(), "icons/a.svg" to evil))
        try {
            store.install(file)
            fail("DOCTYPE in SVG must be rejected")
        } catch (expected: IconPackException) {
            assertTrue(store.list().isEmpty())
        }
    }

    @Test
    fun `newer version of the same uuid replaces the old one`() {
        setUp()
        val uuid = UUID.randomUUID().toString()
        store.install(validPack(uuid = uuid, version = 1))
        store.install(validPack(uuid = uuid, version = 2))
        val listed = store.list()
        assertEquals(1, listed.size)
        assertEquals(2, listed[0].version)
        assertFalse(File(packsDir, "$uuid/1").exists())
        assertTrue(File(packsDir, "$uuid/2").exists())
    }

    @Test
    fun `pack with more than 2000 icons installs`() {
        setUp()
        // Real multi-brand packs pass 2000 entries; the old cap rejected them
        // with "pack.json declares too many icons" before reading anything.
        val icons = (1..2501).map { i -> "icons/$i.svg" to svg }
        val installed = store.install(validPack(icons = icons))
        assertEquals(2501, installed.iconCount)
    }

    @Test
    fun `date style pack version installs`() {
        setUp()
        // aegis-icons ships "version": 20260701; the old 999999 cap rejected
        // every official pack before its icons were even looked at.
        val installed = store.install(validPack(version = 20260701))
        assertEquals(20260701, installed.version)
        assertEquals(20260701, store.list().single().version)
    }

    @Test
    fun `removing a pack deletes its directory`() {
        setUp()
        val installed = store.install(validPack())
        store.remove(installed)
        assertTrue(store.list().isEmpty())
        assertFalse(File(packsDir, installed.uuid).exists())
    }

    @Test
    fun `safe relative path rules`() {
        assertTrue(IconPackFormat.isSafeRelativePath("icons/a.svg"))
        assertTrue(IconPackFormat.isSafeRelativePath("a.webp.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath(""))
        assertFalse(IconPackFormat.isSafeRelativePath("/etc/passwd"))
        assertFalse(IconPackFormat.isSafeRelativePath("../a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("icons/../../a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("icons//a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("icons\\a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("C:/a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("icons/\u0000a.svg"))
        assertFalse(IconPackFormat.isSafeRelativePath("a".repeat(300)))
    }

    @Test
    fun `listing ignores directories that are not packs`() {
        setUp()
        packsDir.mkdirs()
        File(packsDir, ".incoming-leftover").mkdirs()
        File(packsDir, "not-a-uuid").mkdirs()
        assertTrue(store.list().isEmpty())
        assertNull(store.list().firstOrNull())
    }
}

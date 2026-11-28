package com.safekey.authenticator.icons

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** One icon inside an installed pack. */
data class PackIcon(
    val name: String,
    val relativePath: String,
    val file: File,
    val type: IconType,
    val issuers: List<String>,
    val category: String?
)

/** A pack that is fully extracted under `files/icons/packs/<uuid>/<version>/`. */
data class InstalledIconPack(
    val uuid: String,
    val name: String,
    val version: Int,
    val directory: File,
    val icons: List<PackIcon>
) {
    val iconCount: Int get() = icons.size
}

/**
 * Installs and lists Aegis-format icon packs.
 *
 * Security model (see the Aegis advisory GHSA-fw8c-jmjv-q6xf, CWE-22, fixed
 * in Aegis 3.4.3): `pack.json` is untrusted input and its `filename` values
 * are never used as a path until they pass [IconPackFormat.isSafeRelativePath]
 * *and* a canonical-path containment check. Only files declared in
 * `pack.json` are extracted; every read is bounded by counted bytes.
 *
 * Installation is staged in a hidden directory next to the packs and moved
 * into place only after the whole pack has been validated, so a rejected or
 * interrupted import never leaves a half-installed pack behind.
 */
class IconPackStore(private val packsDir: File) {

    /** Installs [source] (a ZIP file), replacing any pack with the same UUID. */
    fun install(source: File): InstalledIconPack {
        if (!packsDir.isDirectory && !packsDir.mkdirs()) {
            throw IconPackException("could not create the icon pack directory")
        }
        val incoming = File(packsDir, ".incoming-${UUID.randomUUID()}")
        if (!incoming.mkdirs()) {
            throw IconPackException("could not create a temporary directory")
        }
        try {
            val def = ZipFile(source).use { zip ->
                val index = buildEntryIndex(zip)
                val defEntry = index["pack.json"]
                    ?: throw IconPackException("the ZIP has no pack.json at its root")
                val defBytes = readBounded(zip, defEntry, IconLimits.MAX_PACK_JSON_BYTES)
                val def = IconPackFormat.parse(defBytes)

                var total = 0L
                for (icon in def.icons) {
                    // Re-checked here as well: validation and extraction must
                    // not be able to drift apart.
                    if (!IconPackFormat.isSafeRelativePath(icon.filename)) {
                        throw IconPackException("unsafe icon path in pack.json")
                    }
                    val type = IconType.fromFilename(icon.filename)
                        ?: throw IconPackException("unsupported icon format in pack.json")
                    val entry = index[icon.filename]
                        ?: throw IconPackException("pack.json refers to a missing file")
                    rejectCompressionBomb(entry)

                    val dest = File(incoming, icon.filename)
                    if (!isInside(incoming, dest)) {
                        throw IconPackException("icon path escapes the pack directory")
                    }
                    val parent = dest.parentFile
                    if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                        throw IconPackException("could not create the icon directory")
                    }
                    total += copyBounded(zip, entry, dest, IconLimits.MAX_ICON_FILE_BYTES)
                    if (total > IconLimits.MAX_PACK_TOTAL_BYTES) {
                        throw IconPackException("the icon pack is too large")
                    }
                    if (type == IconType.SVG && containsDoctype(dest)) {
                        // SVG is XML: external entities are not needed by any
                        // real icon and are the vector for XXE/DoS.
                        throw IconPackException("the icon pack contains an unsafe SVG")
                    }
                }

                // Keep the definition next to the extracted files, exactly like
                // Aegis does, so listing never has to re-read the ZIP.
                File(incoming, "pack.json").writeBytes(defBytes)
                total += defBytes.size
                if (total > IconLimits.MAX_PACK_TOTAL_BYTES) {
                    throw IconPackException("the icon pack is too large")
                }
                def
            }

            val uuidDir = File(packsDir, def.uuid)
            val target = File(uuidDir, def.version.toString())
            val backup = File(packsDir, ".old-${UUID.randomUUID()}")
            val hadOld = uuidDir.exists()
            if (hadOld && !uuidDir.renameTo(backup)) {
                throw IconPackException("could not replace the existing icon pack")
            }
            if (!uuidDir.mkdirs()) {
                if (hadOld) backup.renameTo(uuidDir)
                throw IconPackException("could not create the icon pack directory")
            }
            if (!incoming.renameTo(target)) {
                uuidDir.deleteRecursively()
                if (hadOld) backup.renameTo(uuidDir)
                throw IconPackException("could not move the icon pack into place")
            }
            backup.deleteRecursively()

            return readInstalled(uuidDir, def.version)
                ?: throw IconPackException("the imported icon pack could not be read back")
        } catch (e: IconPackException) {
            incoming.deleteRecursively()
            throw e
        } catch (e: Exception) {
            incoming.deleteRecursively()
            throw IconPackException("could not read the icon pack ZIP", e)
        }
    }

    /** All installed packs, newest version of each UUID. */
    fun list(): List<InstalledIconPack> {
        val dirs = packsDir.listFiles() ?: return emptyList()
        return dirs.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            if (runCatching { UUID.fromString(dir.name) }.isFailure) return@mapNotNull null
            val versionDir = dir.listFiles()
                ?.filter { it.isDirectory && it.name.toIntOrNull() != null }
                ?.maxByOrNull { it.name.toInt() }
                ?: return@mapNotNull null
            readInstalled(dir, versionDir.name.toInt())
        }.sortedBy { it.name.lowercase() }
    }

    /** Removes a pack; icons already assigned to accounts are unaffected. */
    fun remove(pack: InstalledIconPack) {
        val root = File(packsDir, pack.uuid)
        if (!isInside(packsDir, root)) throw IconPackException("invalid icon pack path")
        if (root.exists() && !root.deleteRecursively()) {
            throw IconPackException("could not delete the icon pack")
        }
    }

    private fun readInstalled(uuidDir: File, version: Int): InstalledIconPack? {
        val versionDir = File(uuidDir, version.toString())
        val defFile = File(versionDir, "pack.json")
        if (!defFile.isFile) return null
        val def = runCatching {
            IconPackFormat.parse(readBoundedFile(defFile, IconLimits.MAX_PACK_JSON_BYTES))
        }.getOrNull() ?: return null
        if (def.uuid != uuidDir.name || def.version != version) return null

        val icons = def.icons.mapNotNull { icon ->
            if (!IconPackFormat.isSafeRelativePath(icon.filename)) return@mapNotNull null
            val type = IconType.fromFilename(icon.filename) ?: return@mapNotNull null
            val file = File(versionDir, icon.filename)
            if (!file.isFile || !isInside(versionDir, file)) return@mapNotNull null
            PackIcon(
                name = icon.name?.takeIf { it.isNotBlank() }
                    ?: icon.filename.substringAfterLast('/').substringBeforeLast('.'),
                relativePath = icon.filename,
                file = file,
                type = type,
                issuers = icon.issuer.filter { it.isNotBlank() },
                category = icon.category
            )
        }
        return InstalledIconPack(
            uuid = def.uuid,
            name = def.name,
            version = def.version,
            directory = versionDir,
            icons = icons
        )
    }

    private fun buildEntryIndex(zip: ZipFile): Map<String, ZipEntry> {
        val index = HashMap<String, ZipEntry>()
        var count = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (++count > IconLimits.MAX_ZIP_ENTRIES) {
                throw IconPackException("the icon pack ZIP has too many entries")
            }
            if (entry.isDirectory) continue
            // Duplicate names would make ZipFile#getEntry ambiguous; refuse
            // rather than picking whichever one the implementation happens
            // to return.
            if (index.put(entry.name, entry) != null) {
                throw IconPackException("the icon pack ZIP has duplicate entries")
            }
        }
        return index
    }

    private fun rejectCompressionBomb(entry: ZipEntry) {
        val compressed = entry.compressedSize
        val declared = entry.size
        if (compressed > 0 && declared > 0 &&
            declared / compressed > IconLimits.MAX_COMPRESSION_RATIO
        ) {
            throw IconPackException("the icon pack ZIP looks like a compression bomb")
        }
        if (declared > IconLimits.MAX_ICON_FILE_BYTES) {
            throw IconPackException("an icon file is too large")
        }
    }

    private fun readBounded(zip: ZipFile, entry: ZipEntry, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > max) throw IconPackException("an entry in the icon pack is too large")
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun copyBounded(zip: ZipFile, entry: ZipEntry, dest: File, max: Long): Long {
        var total = 0L
        zip.getInputStream(entry).use { input ->
            BufferedOutputStream(FileOutputStream(dest)).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > max) throw IconPackException("an icon file is too large")
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    /** True when [child] resolves inside [parent] (symlinks included). */
    private fun isInside(parent: File, child: File): Boolean {
        return try {
            child.canonicalPath.startsWith(parent.canonicalPath + File.separator)
        } catch (e: Exception) {
            false
        }
    }

    private fun containsDoctype(file: File): Boolean {
        return try {
            // The extraction cap bounds this read at 1 MB, so scanning the
            // whole file (not just the first block) is affordable and a
            // DOCTYPE hidden behind a long comment cannot slip through.
            val text = file.readBytes().toString(Charsets.UTF_8).lowercase()
            "<!doctype" in text || "<!entity" in text
        } catch (e: Exception) {
            true
        }
    }

    private fun readBoundedFile(file: File, max: Long): ByteArray {
        if (file.length() > max) throw IconPackException("pack.json is too large")
        val bytes = file.readBytes()
        if (bytes.size > max) throw IconPackException("pack.json is too large")
        return bytes
    }
}

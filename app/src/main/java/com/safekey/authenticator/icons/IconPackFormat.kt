package com.safekey.authenticator.icons

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Thrown for every rejected pack; the message is user-facing. */
class IconPackException(message: String, cause: Throwable? = null) : Exception(message, cause)

enum class IconType(val mime: String) {
    SVG("image/svg+xml"),
    PNG("image/png"),
    JPEG("image/jpeg");

    companion object {
        /** Extension whitelist. Anything else is not a supported icon. */
        fun fromFilename(path: String): IconType? {
            val dot = path.lastIndexOf('.')
            if (dot <= 0 || dot == path.length - 1) return null
            return when (path.substring(dot + 1).lowercase()) {
                "svg" -> SVG
                "png" -> PNG
                "jpg", "jpeg" -> JPEG
                else -> null
            }
        }

        fun fromMime(mime: String?): IconType? = when (mime?.trim()?.lowercase()) {
            "image/svg+xml" -> SVG
            "image/png" -> PNG
            "image/jpeg", "image/jpg" -> JPEG
            else -> null
        }
    }
}

/**
 * `pack.json`, mirroring the Aegis icon pack format
 * (https://github.com/beemdevelopment/Aegis/blob/master/docs/iconpacks.md):
 * a UUID, a name, a version and a list of icons with a path relative to the
 * ZIP root and the issuer aliases used for automatic matching.
 *
 * Derived from Aegis Authenticator (GPL-3.0-or-later), IconPack.java; rewritten
 * for Osmium with kotlinx.serialization and strict validation (2026-11).
 */
@Serializable
data class IconPackDefinition(
    val uuid: String = "",
    val name: String = "",
    val version: Int = 0,
    val icons: List<IconDefinition> = emptyList()
)

@Serializable
data class IconDefinition(
    val name: String? = null,
    val filename: String = "",
    val category: String? = null,
    val issuer: List<String> = emptyList()
)

object IconPackFormat {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(bytes: ByteArray): IconPackDefinition {
        val def = try {
            json.decodeFromString(IconPackDefinition.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IconPackException("pack.json is not valid icon pack JSON", e)
        }
        validate(def)
        return def
    }

    /**
     * Structural validation. Path safety is checked again (per file) by
     * [IconPackStore]; this only rejects packs that cannot be used at all.
     */
    fun validate(def: IconPackDefinition) {
        if (runCatching { java.util.UUID.fromString(def.uuid) }.isFailure) {
            throw IconPackException("pack.json has an invalid uuid")
        }
        if (def.name.isBlank() || def.name.length > IconLimits.MAX_NAME_LENGTH ||
            def.name.any { it.code < 0x20 }
        ) {
            throw IconPackException("pack.json has an invalid name")
        }
        if (def.version < 0 || def.version > IconLimits.MAX_VERSION) {
            throw IconPackException("pack.json has an invalid version")
        }
        if (def.icons.isEmpty()) {
            throw IconPackException("pack.json declares no icons")
        }
        if (def.icons.size > IconLimits.MAX_ICONS) {
            throw IconPackException("pack.json declares too many icons")
        }
        val seen = HashSet<String>(def.icons.size)
        for (icon in def.icons) {
            if (!isSafeRelativePath(icon.filename)) {
                throw IconPackException("pack.json has an unsafe icon path")
            }
            if (IconType.fromFilename(icon.filename) == null) {
                throw IconPackException("pack.json declares an unsupported icon format")
            }
            if (icon.name != null && (icon.name.length > IconLimits.MAX_NAME_LENGTH ||
                    icon.name.any { it.code < 0x20 })
            ) {
                throw IconPackException("pack.json has an invalid icon name")
            }
            if (icon.category != null && icon.category.length > IconLimits.MAX_NAME_LENGTH) {
                throw IconPackException("pack.json has an invalid category")
            }
            if (icon.issuer.size > 50 || icon.issuer.any { it.length > IconLimits.MAX_NAME_LENGTH }) {
                throw IconPackException("pack.json has an invalid issuer list")
            }
            if (!seen.add(icon.filename)) {
                throw IconPackException("pack.json declares the same file twice")
            }
        }
    }

    /**
     * True when [path] is a plain relative path under the pack root:
     * no absolute paths, no drive letters, no backslashes, no NUL/control
     * characters, no empty/`.`/`..` segments, and a bounded length.
     *
     * This is the first line of defence against the Aegis 3.4.3 advisory
     * (GHSA-fw8c-jmjv-q6xf, CWE-22): a pack could otherwise write outside its
     * own directory and overwrite the vault.
     */
    fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.length > IconLimits.MAX_PATH_LENGTH) return false
        if (path[0] == '/' || path[0] == '\\') return false
        if (path.contains('\\') || path.contains(':') || path.contains('\u0000')) return false
        if (path.any { it.code < 0x20 || it.code == 0x7f }) return false
        val segments = path.split('/')
        return segments.none { it.isEmpty() || it == "." || it == ".." }
    }
}

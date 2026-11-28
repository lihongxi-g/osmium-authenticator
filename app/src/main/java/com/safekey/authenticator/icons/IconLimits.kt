package com.safekey.authenticator.icons

/**
 * Hard limits for everything that comes out of an icon pack.
 *
 * A ZIP has no trustworthy size fields: both the declared entry size and the
 * compression ratio are attacker-controlled. Every limit here is therefore
 * enforced by counting bytes while reading, never by trusting the header.
 *
 * The reference pack (aegis-icons 2026-07-01) is 1.0 MB compressed, ~1.9 MB
 * uncompressed, 1007 icons, largest icon 58 KB, pack.json 204 KB — the limits
 * below leave room for a much larger pack while still bounding memory and disk.
 */
object IconLimits {
    /** Largest single icon file accepted from a pack (SVG/PNG/JPEG). */
    const val MAX_ICON_FILE_BYTES = 1L * 1024 * 1024

    /** Largest pack.json accepted. */
    const val MAX_PACK_JSON_BYTES = 1L * 1024 * 1024

    /** Total uncompressed bytes written for one pack (all icons + pack.json). */
    const val MAX_PACK_TOTAL_BYTES = 64L * 1024 * 1024

    /** Maximum number of icons declared by one pack. */
    const val MAX_ICONS = 2_000

    /** Maximum number of entries in the ZIP itself (declared or not). */
    const val MAX_ZIP_ENTRIES = 20_000

    /** pack.json version must be a small non-negative int (also a path segment). */
    const val MAX_VERSION = 999_999

    /** Reject entries that claim a compression ratio above this. */
    const val MAX_COMPRESSION_RATIO = 200L

    /** Longest relative path accepted for an icon file. */
    const val MAX_PATH_LENGTH = 255

    /** Longest pack/icon name or issuer alias accepted. */
    const val MAX_NAME_LENGTH = 200

    /** Largest icon stored per account (after rasterising to [STORE_PX]). */
    const val MAX_STORED_BYTES = 512 * 1024

    /** Icons are rasterised to this edge length before being stored encrypted. */
    const val STORE_PX = 256

    /** Largest source icon dimension accepted (Aegis uses the same 512 cap). */
    const val MAX_SOURCE_PX = 512

    val ALLOWED_MIME: Set<String> = setOf("image/svg+xml", "image/png", "image/jpeg")
}

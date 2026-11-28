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

    /**
     * Maximum number of icons declared by one pack. Aegis packs are much
     * smaller, but real-world collections (multi-brand packs with one icon per
     * service) pass 2000 icons easily; the byte and ZIP-entry caps are what
     * actually bound disk/memory use.
     */
    const val MAX_ICONS = 20_000

    /** Maximum number of entries in the ZIP itself (declared or not). */
    const val MAX_ZIP_ENTRIES = 40_000

    /**
     * pack.json version must be a non-negative int. There is deliberately no
     * small upper bound: aegis-icons ships `"version": 20260701` (a date), and
     * the version is only ever written as `Int.toString()`, which cannot carry
     * a path separator.
     */
    const val MAX_VERSION = Int.MAX_VALUE

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

    /**
     * Longest edge used while the user crops a gallery image. Big enough to
     * keep a 4x zoom sharp on a 1080 px-wide crop frame, small enough that a
     * 48 MP photo never has to be decoded at full size.
     */
    const val CROP_SOURCE_PX = 1600

    /** Upper bound for a user-picked image (48 MP JPEGs sit around 10–20 MB). */
    const val MAX_CUSTOM_SOURCE_BYTES = 24L * 1024 * 1024

    val ALLOWED_MIME: Set<String> = setOf("image/svg+xml", "image/png", "image/jpeg")
}

package com.safekey.authenticator.tags

/** Validation and normalization rules shared by tag management UI and tests. */
object TagRules {
    const val MAX_NAME_LENGTH = 20

    fun normalizeName(raw: String): String = raw.trim()

    fun isValidName(raw: String): Boolean {
        val name = normalizeName(raw)
        return name.isNotEmpty() && name.codePointCount(0, name.length) <= MAX_NAME_LENGTH
    }

    fun isDuplicate(raw: String, existingNames: Iterable<String>): Boolean {
        val normalized = normalizeName(raw)
        return existingNames.any { normalizeName(it).equals(normalized, ignoreCase = true) }
    }
}

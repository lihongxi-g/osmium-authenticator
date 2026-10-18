package com.safekey.authenticator.totp.importer

/**
 * Minimal RFC 4180 CSV reader: comma separator, double-quoted fields with
 * doubled quotes as escapes, LF/CRLF line endings, blank lines skipped.
 *
 * Enough for the CSV exports of KeePassXC, 1Password, Dashlane and Bitwarden,
 * and deliberately dependency-free so the importers stay pure JVM code that
 * unit-tests run without Android.
 */
internal fun parseCsvRows(content: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    val text = content.removePrefix("\uFEFF")
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
            c == '"' -> inQuotes = true
            c == ',' -> {
                row.add(field.toString())
                field.clear()
            }
            c == '\r' -> Unit
            c == '\n' -> {
                row.add(field.toString())
                field.clear()
                if (row.any { it.isNotBlank() }) rows.add(row)
                row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
    }
    return rows
}

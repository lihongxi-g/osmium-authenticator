package com.safekey.authenticator.totp.importer

/**
 * Registry of all supported authenticator export formats.
 *
 * [find] auto-detects the source format from the file content, so the import
 * UI never has to ask the user which app the export came from.
 */
object Importers {

    /** All registered importers, in detection-priority order. */
    val all: List<AuthenticatorImporter> = listOf(
        AegisImporter,
        TwoFasImporter,
        RaivoImporter
    )

    /** First importer whose [AuthenticatorImporter.detect] matches, or null. */
    fun find(content: String): AuthenticatorImporter? = all.firstOrNull { it.detect(content) }
}

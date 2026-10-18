package com.safekey.authenticator.totp.importer

/**
 * Registry of all supported authenticator export formats.
 *
 * [find] auto-detects the source format from the file content, so the import
 * UI never has to ask the user which app the export came from.
 *
 * Order matters: JSON formats with distinctive keys run before the generic
 * ones, [OtpUriListImporter] before [CsvOtpImporter] (a link list also carries
 * "otpauth://"), and [BitwardenImporter] before [CsvOtpImporter] because a
 * Bitwarden CSV is a special case of a link-carrying CSV.
 */
object Importers {

    /** All registered importers, in detection-priority order. */
    val all: List<AuthenticatorImporter> = listOf(
        AegisImporter,
        TwoFasImporter,
        FreeOtpPlusImporter,
        StratumImporter,
        BitwardenImporter,
        ProtonAuthenticatorImporter,
        RaivoImporter,
        AndOtpImporter,
        OtpUriListImporter,
        CsvOtpImporter
    )

    /** First importer whose [AuthenticatorImporter.detect] matches, or null. */
    fun find(content: String): AuthenticatorImporter? = all.firstOrNull { it.detect(content) }
}

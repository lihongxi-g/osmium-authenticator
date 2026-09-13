package com.safekey.authenticator.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import com.safekey.authenticator.R
import com.safekey.authenticator.integrity.IntegrityCheck
import com.safekey.authenticator.integrity.IntegrityLevel
import com.safekey.authenticator.integrity.IntegritySeverity

/**
 * Localized mapping for the integrity engine's check ids and levels, shared
 * by the report page, the settings entry and the enter-app reminder dialog.
 * Unknown ids (future K2 checks) fall back to the raw id so the UI can never
 * break on engine additions.
 */

fun integrityCheckTitleRes(id: String): Int? = when (id) {
    "apk_signature" -> R.string.check_apk_signature
    "manager_packages" -> R.string.check_manager_packages
    "su_binaries" -> R.string.check_su_binaries
    "root_dirs" -> R.string.check_root_dirs
    "mount_traces" -> R.string.check_mount_traces
    "injected_libs" -> R.string.check_injected_libs
    "kernel_strings" -> R.string.check_kernel_strings
    "boot_props" -> R.string.check_boot_props
    "build_tags" -> R.string.check_build_tags
    "selinux_state" -> R.string.check_selinux_state
    "system_rw" -> R.string.check_system_rw
    "su_runtime" -> R.string.check_su_runtime
    "attestation" -> R.string.check_attestation
    "attestation_boot_hash" -> R.string.check_attestation_boot_hash
    "mount_cross" -> R.string.check_mount_cross
    "file_cross" -> R.string.check_file_cross
    "state_drift" -> R.string.check_state_drift
    else -> null
}

@Composable
fun integrityCheckTitle(id: String): String =
    integrityCheckTitleRes(id)?.let { stringResource(it) } ?: id

/** Status word for one check: miss <-> severity classes. */
fun integrityStatusRes(check: IntegrityCheck): Int = when {
    !check.hit -> R.string.integrity_status_clear
    check.severity == IntegritySeverity.PASS -> R.string.integrity_status_pass
    check.severity == IntegritySeverity.INFO -> R.string.integrity_status_info
    check.severity == IntegritySeverity.WARN -> R.string.integrity_status_warn
    else -> R.string.integrity_status_fail
}

/**
 * Semantic status colors for integrity results: clear/pass reads green and
 * only findings (WARN/FAIL) use the error (red) tone — red is reserved for
 * "something was detected". Informational results stay neutral. Material 3
 * has no built-in success color, so a fixed green pair is picked by surface
 * luminance to stay readable in both light and dark themes.
 */
@Composable
fun integrityStatusColor(hit: Boolean, severity: IntegritySeverity): Color {
    val clear = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        Color(0xFF81C784)
    } else {
        Color(0xFF2E7D32)
    }
    return when {
        !hit -> clear
        severity == IntegritySeverity.PASS -> clear
        severity == IntegritySeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
}

@Composable
fun integrityLevelLabel(level: IntegrityLevel?): String = stringResource(
    when (level) {
        IntegrityLevel.CLEAN -> R.string.integrity_level_clean
        IntegrityLevel.UNVERIFIED -> R.string.integrity_level_unverified
        IntegrityLevel.SUSPICIOUS -> R.string.integrity_level_suspicious
        IntegrityLevel.COMPROMISED -> R.string.integrity_level_compromised
        IntegrityLevel.UNKNOWN -> R.string.integrity_level_unknown
        null -> R.string.loading
    }
)

@Composable
fun integrityLevelDesc(level: IntegrityLevel): String = stringResource(
    when (level) {
        IntegrityLevel.CLEAN -> R.string.integrity_level_clean_desc
        IntegrityLevel.UNVERIFIED -> R.string.integrity_level_unverified_desc
        IntegrityLevel.SUSPICIOUS -> R.string.integrity_level_suspicious_desc
        IntegrityLevel.COMPROMISED -> R.string.integrity_level_compromised_desc
        IntegrityLevel.UNKNOWN -> R.string.integrity_level_unknown_desc
    }
)

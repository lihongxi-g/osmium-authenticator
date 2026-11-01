package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.CenteredScrollColumn

/**
 * Full-screen lock gate. Rendered instead of the app content while the
 * biometric lock is engaged — no account data exists in this composition.
 *
 * Scrolls when the translation and the text size together need more room than
 * the screen has; a centred column without it cut the unlock buttons off.
 */
@Composable
fun LockScreen(
    errorMessage: String?,
    onUnlock: () -> Unit,
    onUsePassword: (() -> Unit)? = null,
    onUsePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CenteredScrollColumn(
        modifier = modifier,
        contentPadding = PaddingValues(32.dp)
    ) {
        Icon(
            imageVector = AppIcons.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onUnlock) {
            Text(stringResource(R.string.unlock))
        }
        if (onUsePin != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onUsePin) {
                Text(stringResource(R.string.use_pin))
            }
        }
        if (onUsePassword != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onUsePassword) {
                Text(stringResource(R.string.biometric_negative))
            }
        }
    }
}

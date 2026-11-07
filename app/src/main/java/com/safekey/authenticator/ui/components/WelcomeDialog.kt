package com.safekey.authenticator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.R

/**
 * One-time welcome, shown the first time a given build is opened
 * (see AppSettings.welcomeShownVersion).
 *
 * Two jobs, both of which otherwise hide three menus deep:
 *  - where Osmium actually lives (website, Telegram, GitHub, F-Droid);
 *  - the two update channels, asked once, in context — with the stable switch
 *    already on, because the user is agreeing to it here rather than by
 *    discovering a setting later.
 *
 * There is a single exit on purpose: dismissing (button, back gesture or a tap
 * outside) saves whatever the switches show, so the dialog can never wedge
 * itself on screen.
 */
@Composable
fun WelcomeDialog(
    initialStable: Boolean,
    initialPreview: Boolean,
    onOpenUrl: (String) -> Unit,
    onFinish: (stable: Boolean, preview: Boolean) -> Unit
) {
    var stable by remember { mutableStateOf(initialStable) }
    var preview by remember { mutableStateOf(initialPreview) }

    AlertDialog(
        onDismissRequest = { onFinish(stable, preview) },
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.welcome_links_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                WelcomeLinkRow(
                    icon = AppIcons.Globe,
                    label = stringResource(R.string.social_website),
                    url = WEBSITE_URL,
                    onOpenUrl = onOpenUrl
                )
                WelcomeLinkRow(
                    icon = AppIcons.Telegram,
                    label = stringResource(R.string.social_telegram),
                    url = TELEGRAM_URL,
                    onOpenUrl = onOpenUrl
                )
                WelcomeLinkRow(
                    icon = AppIcons.GitHub,
                    label = stringResource(R.string.social_github),
                    url = GITHUB_URL,
                    onOpenUrl = onOpenUrl
                )
                // Store name is a brand, not a translatable string.
                WelcomeLinkRow(
                    icon = AppIcons.Android,
                    label = "F-Droid",
                    url = FDROID_URL,
                    onOpenUrl = onOpenUrl
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.welcome_update_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                WelcomeSwitchRow(
                    icon = AppIcons.Refresh,
                    title = stringResource(R.string.update_stable_title),
                    description = stringResource(R.string.update_stable_desc),
                    checked = stable,
                    onCheckedChange = { stable = it }
                )
                WelcomeSwitchRow(
                    icon = AppIcons.Science,
                    title = stringResource(R.string.update_preview_title),
                    description = stringResource(R.string.update_preview_desc),
                    checked = preview,
                    onCheckedChange = { preview = it }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.welcome_update_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onFinish(stable, preview) }) {
                Text(stringResource(R.string.welcome_start))
            }
        }
    )
}

/** Tappable row: icon + project name, opens the link in a browser. */
@Composable
private fun WelcomeLinkRow(
    icon: ImageVector,
    label: String,
    url: String,
    onOpenUrl: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Switch row with its explanation — the same wording as Settings → update checks. */
@Composable
private fun WelcomeSwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val WEBSITE_URL = "https://osmium.im"
private const val TELEGRAM_URL = "https://t.me/osmium2fa"
private const val GITHUB_URL = "https://github.com/lihongxi-g/osmium-authenticator"
private const val FDROID_URL = "https://f-droid.org/packages/com.safekey.authenticator/"
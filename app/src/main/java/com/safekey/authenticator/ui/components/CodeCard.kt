package com.safekey.authenticator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.AccountUi
import com.safekey.authenticator.ui.theme.monospaceFamily

/**
 * One account row: issuer + account name on the left, prominent code +
 * countdown on the right, thin remaining-time progress bar at the bottom.
 *
 * Split into stable (title) and dynamic (code/countdown) sub-composables so
 * per-tick recomposition only touches the parts that actually change.
 */
@Composable
fun CodeCard(
    ui: AccountUi,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier,
    hideCode: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountHeader(
                    title = ui.account.displayTitle,
                    subtitle = ui.account.displaySubtitle,
                    modifier = Modifier.weight(1f)
                )
                CodeDisplay(
                    code = if (hideCode) "\u2022\u2022\u2022\u2022\u2022\u2022" else ui.code,
                    remainingSeconds = ui.remainingSeconds,
                    expiring = ui.remainingSeconds <= 5,
                    onCopyCode = onCopyCode
                )
            }
            LinearProgressIndicator(
                progress = (1f - ui.periodFraction).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                color = if (ui.remainingSeconds <= 5) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/** Static per-account identity — skipped by Compose when unchanged. */
@Composable
private fun AccountHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Live code + countdown — the only part that re-renders each tick. */
@Composable
private fun CodeDisplay(
    code: String,
    remainingSeconds: Int,
    expiring: Boolean,
    onCopyCode: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.clickable(onClickLabel = "Copy code") { onCopyCode() }
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = monospaceFamily,
                letterSpacing = 2.sp
            ),
            maxLines = 1,
            color = if (expiring) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${remainingSeconds}s",
                style = MaterialTheme.typography.labelMedium,
                color = if (expiring) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = AppIcons.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

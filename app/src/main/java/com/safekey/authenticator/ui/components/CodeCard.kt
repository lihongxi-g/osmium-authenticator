package com.safekey.authenticator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * Fixed height so drag-reorder math stays stable.
 */
@Composable
fun CodeCard(
    ui: AccountUi,
    onCopyCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingFraction = 1f - ui.periodFraction
    val expiring = ui.remainingSeconds <= 5

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
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ui.account.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = ui.account.displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            onClickLabel = "Copy code"
                        ) { onCopyCode() }
                    ) {
                        Text(
                            text = ui.code,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = monospaceFamily,
                                letterSpacing = 2.sp
                            ),
                            maxLines = 1,
                            color = if (expiring) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = AppIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 8.dp, end = 8.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${ui.remainingSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (expiring) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = remainingFraction.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                color = if (expiring) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

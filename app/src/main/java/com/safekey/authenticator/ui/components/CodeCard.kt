package com.safekey.authenticator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.safekey.authenticator.AccountUi
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.theme.LocalFontScale
import com.safekey.authenticator.ui.theme.monospaceFamily
import kotlinx.coroutines.delay

/** What a hidden code shows instead of its digits. */
private const val HiddenCodeDots = "\u2022\u2022\u2022\u2022\u2022\u2022"

/** How long a revealed code stays readable before the dots come back. */
private const val RevealTimeoutMs = 6_000L

/**
 * Reveal-on-tap state for accounts whose codes are hidden.
 *
 * Hidden codes used to be readable only by copying them out to another app.
 * Tapping now reveals the digits in place; they go back behind dots after a
 * timeout or as soon as Osmium leaves the foreground, so the setting still
 * keeps codes off screen when nobody asked to see them.
 */
@Stable
class CodeReveal internal constructor(private val state: MutableState<Boolean>) {
    val revealed: Boolean get() = state.value
    fun show() { state.value = true }
    fun hide() { state.value = false }
}

@Composable
fun rememberCodeReveal(): CodeReveal {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(state.value) {
        if (state.value) {
            delay(RevealTimeoutMs)
            state.value = false
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.value = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember { CodeReveal(state) }
}

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
    hideCode: Boolean = false,
    showTags: Boolean = true,
    showIcons: Boolean = false,
    letterIcons: Boolean = true
) {
    val reveal = rememberCodeReveal()
    val masked = hideCode && !reveal.revealed
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
                    uiTags = if (showTags) ui.account.tags else emptyList(),
                    showIcon = showIcons,
                    letterIcons = letterIcons,
                    account = ui.account,
                    modifier = Modifier.weight(1f)
                )
                CodeDisplay(
                    code = if (masked) HiddenCodeDots else ui.code,
                    remainingSeconds = ui.remainingSeconds,
                    expiring = ui.remainingSeconds <= 5,
                    masked = masked,
                    onTap = {
                        if (masked) reveal.show() else onCopyCode()
                    }
                )
            }
            if (!ui.isHotp) {
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
}

/** Static per-account identity — skipped by Compose when unchanged. */
@Composable
private fun AccountHeader(
    title: String,
    subtitle: String,
    uiTags: List<com.safekey.authenticator.model.Tag>,
    account: com.safekey.authenticator.model.Account,
    showIcon: Boolean,
    letterIcons: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIcon) {
            AccountIcon(
                account = account,
                size = 40.dp,
                fallbackLetter = letterIcons,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
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
            if (uiTags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                    uiTags.take(2).forEach { tag ->
                        Text(tag.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (uiTags.size > 2) Text("+${uiTags.size - 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Live code + countdown — the only part that re-renders each tick. */
@Composable
private fun CodeDisplay(
    code: String,
    remainingSeconds: Int,
    expiring: Boolean,
    masked: Boolean,
    onTap: () -> Unit
) {
    val actionLabel = stringResource(if (masked) R.string.tap_to_reveal else R.string.copy_code)
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.clickable(onClickLabel = actionLabel) {
            onTap()
        }
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = monospaceFamily,
                letterSpacing = 2.sp * LocalFontScale.current
            ),
            maxLines = 1,
            color = if (expiring) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (remainingSeconds > 0) "${remainingSeconds}s" else "HOTP",
                style = MaterialTheme.typography.labelMedium,
                color = if (expiring) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (masked) AppIcons.Visibility else AppIcons.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

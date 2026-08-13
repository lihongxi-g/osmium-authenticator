package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons

private const val PIN_LENGTH = 6

/**
 * Shared numeric pad. When [pinLength] digits are entered, [onPinEntered]
 * fires (the parent validates and either completes or shows an error).
 */
@Composable
fun PinPadScreen(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    onPinEntered: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(24.dp))
        }

        // Number pad 3x4
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.chunked(3).forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKeys.forEach { key ->
                        when (key) {
                            "" -> Spacer(Modifier.size(72.dp))
                            "del" -> PinKey(
                                label = "",
                                icon = true,
                                onClick = { pin = pin.dropLast(1) }
                            )
                            else -> PinKey(
                                label = key,
                                icon = false,
                                onClick = {
                                    if (pin.length < PIN_LENGTH) {
                                        pin += key
                                        if (pin.length == PIN_LENGTH) {
                                            onPinEntered(pin)
                                            pin = ""
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (onCancel != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinKey(label: String, icon: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon) {
                androidx.compose.material3.Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.pin_delete),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Full-screen PIN verification gate. */
@Composable
fun PinVerifyScreen(
    title: String,
    subtitle: String?,
    error: String?,
    remainingAttempts: Int?,
    onVerify: (String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PinPadScreen(
            title = title,
            subtitle = subtitle,
            error = error,
            onPinEntered = onVerify,
            onCancel = onCancel
        )
        if (remainingAttempts != null) {
            Text(
                text = stringResource(R.string.pin_attempts_left, remainingAttempts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

/** Two-step PIN setup: enter twice, with an optional validator (e.g. the
 *  self-destruct PIN must differ from the app PIN). */
@Composable
fun PinSetupScreen(
    title: String,
    description: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    onValidate: ((String) -> Boolean)? = null,
    validateError: String? = null
) {
    var step by remember { mutableStateOf(0) }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // resolved once in composition — lambdas below are not composable contexts
    val mismatchText = stringResource(R.string.pin_mismatch)
    val confirmTitle = stringResource(R.string.pin_confirm_title)

    if (step == 0) {
        PinPadScreen(
            title = title,
            subtitle = description,
            error = error,
            onPinEntered = { pin ->
                firstPin = pin
                step = 1
                error = null
            },
            onCancel = onCancel
        )
    } else {
        PinPadScreen(
            title = confirmTitle,
            subtitle = null,
            error = error,
            onPinEntered = { pin ->
                if (pin == firstPin) {
                    if (onValidate == null || onValidate(pin)) {
                        onDone(pin)
                    } else {
                        error = validateError ?: mismatchText
                        step = 0
                        firstPin = ""
                    }
                } else {
                    error = mismatchText
                    step = 0
                    firstPin = ""
                }
            },
            onCancel = onCancel
        )
    }
}

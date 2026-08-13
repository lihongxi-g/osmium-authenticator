package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.totp.Base32
import com.safekey.authenticator.totp.OtpUriParser
import com.safekey.authenticator.totp.ParsedOtpUri
import com.safekey.authenticator.ui.components.SimpleTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormScreen(
    vm: MainViewModel,
    accountId: String?,
    prefillUri: ParsedOtpUri?,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val existing by vm.accounts.collectAsState()
    val editing = accountId != null

    var issuer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var algorithm by remember { mutableStateOf(Account.ALGO_SHA1) }
    var digits by remember { mutableStateOf(6) }
    var period by remember { mutableStateOf("30") }
    var error by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }

    // Initialize from the account being edited
    LaunchedEffect(accountId) {
        if (accountId != null) {
            val account = existing.firstOrNull { it.id == accountId }
            if (account != null) {
                issuer = account.issuer
                label = account.label
                secret = account.secret
                algorithm = account.algorithm
                digits = account.digits
                period = account.period.toString()
            }
        } else if (prefillUri != null) {
            issuer = prefillUri.issuer
            label = prefillUri.label
            secret = prefillUri.secret
            algorithm = prefillUri.algorithm
            digits = prefillUri.digits
            period = prefillUri.period.toString()
        }
        initialized = true
    }

    // Paste mode: try to parse the clipboard as otpauth:// on first open
    if (prefillUri == null && accountId == null && !initialized) {
        // nothing — wait for the LaunchedEffect above
    }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(if (editing) R.string.edit else R.string.add_account),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(stringResource(R.string.issuer_label)) },
                placeholder = { Text("Google") },
                singleLine = true,
                isError = error != null && issuer.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.account_name_label)) },
                placeholder = { Text("example@gmail.com") },
                singleLine = true,
                isError = error != null && label.isBlank(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it.uppercase() },
                label = { Text(stringResource(R.string.secret_label)) },
                placeholder = { Text("JBSWY3DPEHPK3PXP") },
                singleLine = true,
                isError = error != null && !Base32.isValid(secret),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = stringResource(R.string.algorithm_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Account.SUPPORTED_ALGORITHMS.forEach { algo ->
                    FilterChip(
                        selected = algorithm == algo,
                        onClick = { algorithm = algo },
                        label = { Text(algo) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.digits_label),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Account.SUPPORTED_DIGITS.forEach { d ->
                    FilterChip(
                        selected = digits == d,
                        onClick = { digits = d },
                        label = { Text("$d") }
                    )
                }
            }

            OutlinedTextField(
                value = period,
                onValueChange = { period = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.period_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = error != null && (period.toIntOrNull() ?: 0) !in 1..600,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val errorId = validate(issuer, label, secret, period)
                    if (errorId == null) {
                        val p = period.toInt()
                        if (editing && accountId != null) {
                            vm.updateAccount(accountId, issuer, label, secret, algorithm, digits, p)
                        } else {
                            vm.addAccount(issuer, label, secret, algorithm, digits, p)
                        }
                        onDone()
                    } else {
                        error = stringResource(errorId)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

private fun validate(issuer: String, label: String, secret: String, period: String): Int? {
    return when {
        issuer.isBlank() -> R.string.error_issuer_required
        label.isBlank() -> R.string.error_name_required
        !Base32.isValid(secret) -> R.string.error_secret_invalid
        else -> {
            val bytes = try { Base32.decode(secret) } catch (_: Exception) { ByteArray(0) }
            val p = period.toIntOrNull() ?: 0
            when {
                bytes.size in 1..9 -> R.string.error_secret_too_short
                p !in 1..600 -> R.string.error_period_invalid
                else -> null
            }
        }
    }
}

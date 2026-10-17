package com.safekey.authenticator.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bybit deposit addresses. Kept in code (not strings) so no locale can alter them. */
private const val USDT_TRC20 = "THutAdwtE1mA4VbhTwfE4gzCtWeQbhTUnf"
private const val USDT_ERC20 = "0x2d78107b4b9e113dc71bc5f9264719c29b34e7f6"
private const val LIBERAPAY_URL = "https://liberapay.com/lihongxi-g/donate"

/**
 * Donation page (second-level page reached from About).
 *
 * Deliberate constraints:
 *  - no network code of its own: Liberapay opens in the system browser, so the
 *    app keeps zero tracking surface and needs no embedded web view
 *  - the WeChat reward image is a bundled asset; tapping it opens a viewer that
 *    can save it to the gallery (MediaStore on Android 10+, SAF picker below
 *    that — no storage permission is requested)
 *  - crypto addresses are shown verbatim in monospace with an explicit network
 *    warning, because a wrong network loses the funds
 *  - donating unlocks nothing; the disclaimer says so in plain language
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReward by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { writeRewardToUri(context, uri) }
            vm.showToast(
                context.getString(
                    if (ok) R.string.donation_save_ok else R.string.donation_save_failed
                )
            )
        }
    }

    fun saveReward() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { saveRewardToGallery(context) }
                vm.showToast(
                    context.getString(
                        if (ok) R.string.donation_save_ok else R.string.donation_save_failed
                    )
                )
            }
        } else {
            // Android 8/9: let the user pick the destination (SAF) instead of
            // asking for WRITE_EXTERNAL_STORAGE just to save one picture.
            saveLauncher.launch("osmium-wechat-reward.png")
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.donation_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.donation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
            )

            // ---- Disclaimer -------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.donation_disclaimer_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.donation_disclaimer_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Liberapay --------------------------------------------------
            DonationSectionTitle("Liberapay")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LIBERAPAY_URL))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            vm.showToast(context.getString(R.string.no_browser))
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liberapay",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.donation_liberapay_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.donation_liberapay_open),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ---- Crypto -----------------------------------------------------
            DonationSectionTitle(stringResource(R.string.donation_crypto_title))
            AddressCard(
                label = stringResource(R.string.donation_network_trc20),
                address = USDT_TRC20
            ) {
                ClipboardHelper.copy(context, USDT_TRC20)
                vm.showToast(context.getString(R.string.donation_copied))
            }
            Spacer(Modifier.height(8.dp))
            AddressCard(
                label = stringResource(R.string.donation_network_erc20),
                address = USDT_ERC20
            ) {
                ClipboardHelper.copy(context, USDT_ERC20)
                vm.showToast(context.getString(R.string.donation_copied))
            }
            Text(
                text = stringResource(R.string.donation_crypto_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )

            // ---- WeChat reward code ----------------------------------------
            DonationSectionTitle(stringResource(R.string.donation_wechat_title))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReward = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(R.drawable.wechat_reward),
                        contentDescription = stringResource(R.string.donation_wechat_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.donation_wechat_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.donation_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showReward) {
        RewardCodeViewer(
            onDismiss = { showReward = false },
            onSave = { saveReward() }
        )
    }
}

@Composable
private fun DonationSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun AddressCard(label: String, address: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.size(10.dp))
            Icon(
                imageVector = AppIcons.ContentCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Full-screen viewer: tap outside to close, button saves to the gallery. */
@Composable
private fun RewardCodeViewer(onDismiss: () -> Unit, onSave: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.wechat_reward),
                    contentDescription = stringResource(R.string.donation_wechat_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSave) {
                        Text(
                            text = stringResource(R.string.donation_save),
                            color = Color.White
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.donation_close),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ---- Reward image persistence ------------------------------------------------

private fun loadRewardBitmap(context: Context): Bitmap? = try {
    BitmapFactory.decodeResource(context.resources, R.drawable.wechat_reward)
} catch (_: Throwable) {
    null
}

/** Android 10+: write straight into Pictures/Osmium via MediaStore (no permission). */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveRewardToGallery(context: Context): Boolean {
    val bitmap = loadRewardBitmap(context) ?: return false
    val values = ContentValues().apply {
        put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "osmium-wechat-reward-${System.currentTimeMillis()}.png"
        )
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${android.os.Environment.DIRECTORY_PICTURES}/Osmium"
        )
    }
    return try {
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return false
        true
    } catch (_: Throwable) {
        false
    }
}

/** Android 8/9: write to the URI the user picked in the document picker. */
private fun writeRewardToUri(context: Context, uri: Uri): Boolean {
    val bitmap = loadRewardBitmap(context) ?: return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } != null
    } catch (_: Throwable) {
        false
    }
}

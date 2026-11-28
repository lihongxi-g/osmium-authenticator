package com.safekey.authenticator.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.icons.IconBitmapCache
import com.safekey.authenticator.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Circular entry icon. Decoding happens off the main thread and is cached by
 * content hash (see [IconBitmapCache]); the bitmap comes back already square
 * and circle-masked, so drawing it is a plain 1:1 bitmap draw — no draw-time
 * clip that would shimmer while the list scrolls.
 *
 * When the account has no icon and [fallbackLetter] is set, a first-letter
 * avatar in a theme colour is drawn instead. That fallback is what makes an
 * unmatched entry look intentional rather than empty, and it can be turned off
 * separately from icons (Settings → Appearance → Icons).
 */
@Composable
fun AccountIcon(
    account: Account,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    fallbackLetter: Boolean = false
) {
    val bytes = account.icon
    if (bytes == null || bytes.isEmpty()) {
        if (fallbackLetter) LetterIcon(account = account, size = size, modifier = modifier)
        return
    }

    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(16, 512)
    val key = remember(bytes, px) { IconBitmapCache.key(bytes, px) }
    val mime = account.iconMime
    val bitmap by produceState(initialValue = IconBitmapCache.peek(key)?.asImageBitmap(), key1 = key) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                IconBitmapCache.getOrDecode(bytes, mime, px)?.asImageBitmap()
            }
        }
    }

    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    }
}

/**
 * First-letter fallback avatar. The colour comes from the active Material You
 * palette (so it follows wallpaper dynamic colour and light/dark theme), and
 * the letter size is independent of the app's font-size setting — a 40 dp
 * avatar must not clip at 200 % text size.
 */
@Composable
private fun LetterIcon(account: Account, size: Dp, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val index = remember(account.id) { Math.floorMod(account.id.hashCode(), 3) }
    val background = when (index) {
        0 -> scheme.primaryContainer
        1 -> scheme.secondaryContainer
        else -> scheme.tertiaryContainer
    }
    val foreground = when (index) {
        0 -> scheme.onPrimaryContainer
        1 -> scheme.onSecondaryContainer
        else -> scheme.onTertiaryContainer
    }
    val letter = remember(account.displayTitle) {
        val title = account.displayTitle.trim()
        if (title.isEmpty()) "?"
        else String(Character.toChars(title.codePointAt(0))).uppercase()
    }
    val fontScale = LocalDensity.current.fontScale
    val fontSize = (size.value * 0.42f / fontScale).sp

    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = foreground,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

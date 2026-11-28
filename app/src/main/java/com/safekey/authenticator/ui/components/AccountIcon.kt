package com.safekey.authenticator.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.icons.IconBitmapCache
import com.safekey.authenticator.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Circular entry icon. Decoding happens off the main thread and is cached by
 * content hash (see [IconBitmapCache]); when no icon is assigned nothing is
 * emitted, so callers fall back to their text-only layout.
 */
@Composable
fun AccountIcon(
    account: Account,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val bytes = account.icon
    if (bytes == null || bytes.isEmpty()) return

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
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}

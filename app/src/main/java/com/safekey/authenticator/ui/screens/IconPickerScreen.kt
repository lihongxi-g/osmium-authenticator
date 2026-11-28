package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.icons.IconBitmapCache
import com.safekey.authenticator.icons.IconMatcher
import com.safekey.authenticator.icons.PackIcon
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen icon picker over the installed Aegis-format packs.
 *
 * Layout follows Aegis' picker: search field on top, pack switcher when more
 * than one pack is installed, then a grid whose first items are the issuer
 * suggestions (Aegis rule + Chinese alias table), followed by every icon.
 * Picking writes the rasterised icon to the account immediately and returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerScreen(
    vm: MainViewModel,
    accountId: String,
    issuer: String,
    onBack: () -> Unit
) {
    val packs by vm.iconPacks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var packIndex by rememberSaveable { mutableStateOf(0) }

    val pack = packs.getOrNull(packIndex.coerceIn(0, (packs.size - 1).coerceAtLeast(0)))
    val suggested = remember(pack?.uuid, pack?.version, issuer) {
        pack?.let { IconMatcher.suggestions(it, issuer) }.orEmpty()
    }
    val icons = remember(pack?.uuid, pack?.version, query, suggested) {
        when {
            pack == null -> emptyList()
            query.isBlank() -> {
                val seen = suggested.mapTo(HashSet()) { it.relativePath }
                suggested + pack.icons.filter { it.relativePath !in seen }
            }
            else -> IconMatcher.search(pack, query, 400)
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.icon_pick_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pack == null) {
                Text(
                    text = stringResource(R.string.icon_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = stringResource(R.string.icon_pick_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.icon_pick_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (packs.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    packs.forEachIndexed { index, item ->
                        FilterChip(
                            selected = index == packIndex,
                            onClick = {
                                packIndex = index
                                query = ""
                            },
                            label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "__none__") {
                    IconTile(
                        label = stringResource(R.string.icon_pick_none),
                        icon = null,
                        onClick = {
                            vm.clearAccountIcon(accountId)
                            onBack()
                        }
                    )
                }
                items(icons, key = { it.relativePath }) { icon ->
                    IconTile(
                        label = icon.name,
                        icon = icon,
                        onClick = {
                            vm.setAccountIconFromPack(accountId, icon) { onBack() }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTile(label: String, icon: PackIcon?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                PackIconBitmap(icon = icon, size = 48.dp)
            } else {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PackIconBitmap(icon: PackIcon, size: Dp) {
    val bitmap by produceState(initialValue = null, key1 = icon.file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = icon.file.readBytes()
                IconBitmapCache.getOrDecode(bytes, icon.type.mime, 128)
            }.getOrNull()?.asImageBitmap()
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}

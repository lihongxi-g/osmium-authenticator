package com.safekey.authenticator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.icons.IconBitmapCache
import com.safekey.authenticator.icons.IconMatcher
import com.safekey.authenticator.icons.InstalledIconPack
import com.safekey.authenticator.icons.PackIcon
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One icon plus the pack it came from, so keys stay unique across packs. */
private data class PickIcon(val packUuid: String, val icon: PackIcon) {
    val key: String get() = packUuid + "/" + icon.relativePath
}

/**
 * Full-screen icon picker over the installed Aegis-format packs.
 *
 * Every pack that is switched on in Settings → Appearance → Icons contributes
 * icons here, so two packs can be used together to fill each other's gaps. The
 * chips filter the grid to one pack ("All packs" is the default when more than
 * one is enabled). Layout follows Aegis' picker: search field on top, then a
 * grid whose first items are "No icon" and a gallery import, followed by the
 * issuer suggestions (Aegis rule + Chinese alias table) and every icon.
 *
 * Gallery import opens [IconCropScreen] first; the cropped square is written
 * to the account, then the crop and picker screens both close.
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
    val settings by vm.settings.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var packFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val enabledPacks = remember(packs, settings.disabledIconPacks) {
        packs.filter { it.uuid !in settings.disabledIconPacks }
    }
    // A pack that was switched off while the picker was open must not remain
    // selected, and a single remaining pack needs no filter chip.
    LaunchedEffect(enabledPacks, packFilter) {
        if (packFilter != null && enabledPacks.none { it.uuid == packFilter }) {
            packFilter = null
        }
    }
    val activePacks = remember(enabledPacks, packFilter) {
        packFilter?.let { selected -> enabledPacks.filter { it.uuid == selected } }
            ?: enabledPacks
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.nav.push(Screen.IconCrop(accountId, uri.toString()))
        }
    }

    val packKey = activePacks.joinToString("|") { it.uuid + ":" + it.version }
    val (_, icons) = remember(packKey, query, issuer) {
        mergeIcons(activePacks, issuer, query)
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.icon_pick_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (enabledPacks.isEmpty()) {
                Text(
                    text = stringResource(
                        if (packs.isEmpty()) R.string.icon_pick_empty else R.string.icon_packs_all_off
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                Text(
                    text = stringResource(
                        if (packs.isEmpty()) R.string.icon_pick_import_hint
                        else R.string.icon_pick_turn_on_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (enabledPacks.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.icon_pick_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (enabledPacks.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        FilterChip(
                            selected = packFilter == null,
                            onClick = { packFilter = null },
                            label = {
                                Text(
                                    stringResource(R.string.icon_pick_all_packs),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                        enabledPacks.forEach { item ->
                            FilterChip(
                                selected = packFilter == item.uuid,
                                onClick = { packFilter = item.uuid },
                                label = {
                                    Text(
                                        item.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
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
                    GlyphTile(
                        label = stringResource(R.string.icon_pick_none),
                        vector = AppIcons.Close,
                        onClick = {
                            vm.clearAccountIcon(accountId)
                            onBack()
                        }
                    )
                }
                item(key = "__custom__") {
                    GlyphTile(
                        label = stringResource(R.string.icon_custom_pick),
                        vector = AppIcons.ImageIcon,
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }
                items(icons, key = { it.key }) { picked ->
                    IconTile(
                        label = picked.icon.name,
                        icon = picked.icon,
                        onClick = {
                            vm.setAccountIconFromPack(accountId, picked.icon) { onBack() }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Combines the enabled packs into the two lists the grid shows: suggestions
 * for this issuer (before the full set) and every icon or search result.
 *
 * With several packs, duplicates are removed by icon name so a 1000-icon pack
 * dropped next to another does not show the same brand twice. Selecting a
 * single pack chip still exposes that pack's own variant.
 */
private fun mergeIcons(
    packs: List<InstalledIconPack>,
    issuer: String,
    query: String
): Pair<List<PickIcon>, List<PickIcon>> {
    val dedupeNames = packs.size > 1
    val suggested = ArrayList<PickIcon>()
    val seen = HashSet<String>()

    fun add(target: MutableList<PickIcon>, pack: InstalledIconPack, icon: PackIcon): Boolean {
        val dedupeKey = if (dedupeNames) IconMatcher.normalize(icon.name)
        else pack.uuid + "/" + icon.relativePath
        if (!seen.add(dedupeKey)) return false
        target.add(PickIcon(pack.uuid, icon))
        return true
    }

    if (query.isNotBlank()) {
        val results = ArrayList<PickIcon>()
        outer@ for (pack in packs) {
            for (icon in IconMatcher.search(pack, query, 400)) {
                add(results, pack, icon)
                if (results.size >= 400) break@outer
            }
        }
        return emptyList<PickIcon>() to results
    }

    for (pack in packs) {
        for (icon in IconMatcher.suggestions(pack, issuer)) {
            add(suggested, pack, icon)
        }
    }
    val all = ArrayList(suggested)
    for (pack in packs) {
        for (icon in pack.icons) {
            add(all, pack, icon)
        }
    }
    return suggested to all
}

@Composable
private fun IconTile(label: String, icon: PackIcon?, onClick: () -> Unit) {
    IconTileFrame(label = label, onClick = onClick) {
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
}

/** A picker tile that is not backed by a pack icon (none / gallery). */
@Composable
private fun GlyphTile(label: String, vector: ImageVector, onClick: () -> Unit) {
    IconTileFrame(label = label, onClick = onClick) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconTileFrame(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            content()
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
    // Decode at the exact slot size: a fixed 128 px bitmap drawn into a
    // 48 dp (≈144 px) slot was being upscaled, which made the grid soft.
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceIn(16, 512)
    val key = remember(icon.file.path, px) { icon.file.path + "@" + px }
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = icon.file.readBytes()
                IconBitmapCache.getOrDecode(bytes, icon.type.mime, px)
            }.getOrNull()?.asImageBitmap()
        }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    }
}

package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.safekey.authenticator.AccountUi
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.CodeCard
import com.safekey.authenticator.ui.components.FilterChipRow
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBarsPadding
import com.safekey.authenticator.tags.TagBatch
import com.safekey.authenticator.tags.TagPresentation
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.ui.components.TagColorDot
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.Saver

private val ItemSpacingPx = 8.dp

/**
 * Floor, not a fixed height: the text-size setting can grow the title block
 * (title + subtitle + tag row) past what 96dp holds, and a fixed height would
 * clip it. At the default step the content is shorter than this, so the floor
 * wins and the card looks exactly as before.
 */
private val CardMinHeightPx = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    vm: MainViewModel,
    listState: LazyListState,
    tagRowState: ScrollState,
    onAddScan: () -> Unit,
    onAddManual: () -> Unit,
    onAddPaste: () -> Unit,
    onOpenDetail: (Account) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiList by vm.filteredSortedAccountUiList.collectAsState()
    val allUiList by vm.sortedAccountUiList.collectAsState()
    val accountsLoaded by vm.accountsLoaded.collectAsState()
    val tags by vm.tags.collectAsState()
    val selectedTagIds by vm.selectedTagIds.collectAsState()
    val settings by vm.settings.collectAsState()
    val search by vm.searchQuery.collectAsState()
    // Saveable: the query lives in the ViewModel, so losing this flag across a
    // gate round-trip left the list filtered with no visible search bar.
    var searching by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    // Batch tagging: long-press a card (or the palette action) starts the mode,
    // then taps toggle accounts instead of opening them. Saveable so a lock-gate
    // round-trip does not lose a half-built selection; ids are UUIDs, so a
    // comma-joined string is a safe encoding.
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(
        stateSaver = Saver<Set<String>, String>(
            restore = { value -> value.split(",").filter { it.isNotEmpty() }.toSet() },
            save = { value -> value.joinToString(",") }
        )
    ) { mutableStateOf(emptySet()) }
    var showBatchTag by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val filtered = uiList
    // An account can disappear under the selection (deleted, or hidden by a
    // filter), so drop ids that no longer exist: the count in the bar must not
    // promise more accounts than the batch write will touch.
    LaunchedEffect(allUiList.size) {
        if (selectionMode) selectedIds = TagBatch.retainExisting(selectedIds, allUiList.map { it.account.id })
    }
    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                val allVisibleSelected = selectedIds.containsAll(filtered.map { it.account.id })
                SimpleTopBar(
                    title = stringResource(R.string.batch_select_count, selectedIds.size),
                    onBack = { exitSelection() },
                    actions = {
                        IconButtonCompat(
                            icon = AppIcons.Check,
                            contentDescription = stringResource(
                                if (allVisibleSelected) R.string.batch_select_clear else R.string.batch_select_all
                            ),
                            onClick = {
                                selectedIds = TagBatch.toggleAll(selectedIds, filtered.map { it.account.id })
                            }
                        )
                    }
                )
            } else if (searching) {
                SearchTopBar(
                    query = search,
                    onQueryChange = vm::setSearchQuery,
                    onClose = {
                        vm.setSearchQuery("")
                        searching = false
                    }
                )
            } else {
                SimpleTopBar(
                    title = stringResource(R.string.app_name),
                    actions = {
                        if (settings.tagsEnabled && tags.isNotEmpty()) {
                            IconButtonCompat(
                                icon = AppIcons.Palette,
                                contentDescription = stringResource(R.string.batch_select_enter),
                                onClick = {
                                    selectionMode = true
                                    selectedIds = emptySet()
                                }
                            )
                        }
                        IconButtonCompat(
                            icon = AppIcons.Search,
                            contentDescription = stringResource(R.string.search_hint),
                            onClick = { searching = true }
                        )
                        IconButtonCompat(
                            icon = AppIcons.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            onClick = onOpenSettings
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            // Hidden while selecting: adding an account mid-selection only
            // gets in the way, and the FAB covers the last row's tap target.
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.add_account))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (TagPresentation.shouldShowFilterRow(settings.tagsEnabled, tags)) {
                FilterChipRow(tags, selectedTagIds, vm, tagRowState)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when {
                !accountsLoaded -> LoadingState(
                    modifier = Modifier.align(Alignment.Center)
                )
                allUiList.isEmpty() -> EmptyState(
                    onAdd = { showAddSheet = true },
                    modifier = Modifier.align(Alignment.Center)
                )
                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> AccountList(
                    items = filtered,
                    hideCodes = settings.hideCodes,
                    showTags = settings.tagsEnabled,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onCopyCode = { ui ->
                        ClipboardHelper.copy(context, ui.code)
                        vm.incrementCopyCount(ui.account.id)
                        vm.showToast(context.getString(R.string.code_copied))
                    },
                    onOpen = onOpenDetail,
                    onToggleSelect = { id -> selectedIds = TagBatch.toggle(selectedIds, id) },
                    onStartSelection = { id ->
                        selectionMode = true
                        selectedIds = setOf(id)
                    },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (selectionMode) 16.dp else 96.dp
                    ),
                    state = listState
                )
            }
            }
            if (selectionMode) {
                SelectionBar(
                    selectedCount = selectedIds.size,
                    canTag = tags.isNotEmpty(),
                    onTag = { showBatchTag = true }
                )
            }
        }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    if (showBatchTag) {
        BatchTagDialog(
            tags = tags,
            accountCount = selectedIds.size,
            onDismiss = { showBatchTag = false },
            onApply = { tagIds, add ->
                if (add) vm.addTagsToAccounts(selectedIds, tagIds) else vm.removeTagsFromAccounts(selectedIds, tagIds)
                vm.showToast(context.getString(R.string.batch_tag_done, selectedIds.size))
                showBatchTag = false
                exitSelection()
            }
        )
    }

    if (showAddSheet) {
        AddAccountSheet(
            onScan = {
                showAddSheet = false
                onAddScan()
            },
            onManual = {
                showAddSheet = false
                onAddManual()
            },
            onPaste = {
                showAddSheet = false
                onAddPaste()
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
        IconButtonCompat(
            icon = AppIcons.Close,
            contentDescription = stringResource(R.string.close),
            onClick = onClose
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = AppIcons.Security,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        ExtendedFloatingActionButton(
            onClick = onAdd,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(AppIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.empty_action)) }
        )
    }
}

/** Shown while the account feed is still loading and decrypting (cold
 *  start). Replaces the empty-state flash; the real empty state only
 *  renders after the feed has actually delivered a batch. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    onScan: () -> Unit,
    onManual: () -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.add_account),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            AddSheetRow(
                icon = AppIcons.QrCodeScanner,
                title = stringResource(R.string.scan_qr),
                onClick = onScan
            )
            AddSheetRow(
                icon = AppIcons.Keyboard,
                title = stringResource(R.string.enter_manually),
                onClick = onManual
            )
            AddSheetRow(
                icon = AppIcons.ContentPaste,
                title = stringResource(R.string.paste_uri),
                onClick = onPaste
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddSheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Plain (non-draggable) account list — ordering comes from the sort mode
 *  setting instead of drag gestures. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountList(
    items: List<AccountUi>,
    hideCodes: Boolean,
    showTags: Boolean,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onCopyCode: (AccountUi) -> Unit,
    onOpen: (Account) -> Unit,
    onToggleSelect: (String) -> Unit,
    onStartSelection: (String) -> Unit,
    contentPadding: PaddingValues,
    state: LazyListState
) {
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ItemSpacingPx)
    ) {
        items(items, key = { it.account.id }) { ui ->
            // In selection mode a tap toggles the row and copying is disabled:
            // the code area is small enough that a stray tap would otherwise
            // copy instead of adding the account to the selection.
            val copyAction: (() -> Unit)? = if (selectionMode) null else ({ onCopyCode(ui) })
            val longPress: (() -> Unit)? = if (selectionMode) null else ({ onStartSelection(ui.account.id) })
            CodeCard(
                ui = ui,
                hideCode = hideCodes,
                showTags = showTags,
                selectable = selectionMode,
                selected = ui.account.id in selectedIds,
                onCopyCode = copyAction,
                modifier = Modifier
                    .heightIn(min = CardMinHeightPx)
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) onToggleSelect(ui.account.id) else onOpen(ui.account)
                        },
                        onLongClick = longPress,
                        onLongClickLabel = null
                    )
            )
        }
    }
}

/** Bottom action bar of the selection mode: count on the left, tag action right. */
@Composable
private fun SelectionBar(selectedCount: Int, canTag: Boolean, onTag: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.batch_select_count, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onTag, enabled = selectedCount > 0 && canTag) {
                Text(stringResource(R.string.batch_tag_action))
            }
        }
    }
}

/**
 * Batch tag editor: pick one or more tags, then add them to every selected
 * account (or remove them). Adding unions with whatever each account already
 * carries — an account keeps its other tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchTagDialog(
    tags: List<Tag>,
    accountCount: Int,
    onDismiss: () -> Unit,
    onApply: (tagIds: Set<String>, add: Boolean) -> Unit
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batch_tag_title, accountCount)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tags_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in picked,
                                onClick = { picked = TagBatch.toggle(picked, tag.id) },
                                label = { Text(tag.name) },
                                leadingIcon = { TagColorDot(tag.color) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onApply(picked, false) }, enabled = picked.isNotEmpty()) {
                            Text(stringResource(R.string.batch_tag_remove))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(picked, true) }, enabled = picked.isNotEmpty()) {
                Text(stringResource(R.string.batch_tag_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

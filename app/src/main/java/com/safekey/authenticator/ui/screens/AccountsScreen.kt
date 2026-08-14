package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBarsPadding

private val ItemSpacingPx = 8.dp
private val CardHeightPx = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    vm: MainViewModel,
    onAddScan: () -> Unit,
    onAddManual: () -> Unit,
    onAddPaste: () -> Unit,
    onOpenDetail: (Account) -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiList by vm.accountUiList.collectAsState()
    val search by vm.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val filtered = remember(uiList, search) {
        if (search.isBlank()) uiList
        else uiList.filter {
            it.account.issuer.contains(search, ignoreCase = true) ||
                it.account.label.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            if (searching) {
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
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.add_account))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiList.isEmpty() -> EmptyState(
                    onAdd = { showAddSheet = true },
                    modifier = Modifier.align(Alignment.Center)
                )
                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> ReorderableAccountList(
                    items = filtered,
                    onCopyCode = { ui ->
                        ClipboardHelper.copy(context, ui.code)
                        vm.showToast(context.getString(R.string.code_copied))
                    },
                    onOpen = onOpenDetail,
                    onReorder = { ids -> vm.reorderAccounts(ids) },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
                )
            }
        }
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
        modifier = modifier.padding(32.dp),
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

/**
 * Account list with long-press drag reordering.
 *
 * Drag math: every item has a fixed visual height (card + spacing), so the
 * dragged item's render offset can be computed as
 * (startIndex - currentIndex) * itemHeight + fingerDelta — visually continuous
 * while the underlying list order swaps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableAccountList(
    items: List<AccountUi>,
    onCopyCode: (AccountUi) -> Unit,
    onOpen: (Account) -> Unit,
    onReorder: (List<String>) -> Unit,
    contentPadding: PaddingValues
) {
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(0) }
    var fingerDelta by remember { mutableStateOf(0f) }
    // Where on the card the finger pressed down — the card's own offset is
    // finger travel MINUS this, otherwise the card jumps on touch-down and
    // the drop slot is miscalculated.
    var pressOffsetY by remember { mutableStateOf(0f) }

    // Stable across the 500ms code tick: Account objects don't change, so
    // this list is equals-stable and doesn't trigger re-sorting/reordering
    // every tick (which caused flicker while dragging).
    val accounts = remember(items) { items.map { it.account } }

    // Stable ordering key — only changes on real drag operations.
    val orderedIds = remember(accounts, pendingOrder) {
        val order = pendingOrder
        if (order != null) accounts.sortedBy { order.indexOf(it.id) }.map { it.id }
        else accounts.map { it.id }
    }

    // Per-tick UI values (codes/seconds refresh) keyed by the stable order.
    val displayItems = remember(orderedIds, items) {
        orderedIds.mapNotNull { id -> items.firstOrNull { it.account.id == id } }
    }

    // Clear pendingOrder once the persisted order matches (DB write completed)
    LaunchedEffect(orderedIds, pendingOrder) {
        val order = pendingOrder ?: return@LaunchedEffect
        if (orderedIds == order) pendingOrder = null
    }

    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (CardHeightPx + ItemSpacingPx).toPx()
    }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ItemSpacingPx)
    ) {
        items(displayItems, key = { it.account.id }) { ui ->
            val index = displayItems.indexOfFirst { it.account.id == ui.account.id }
            // The gesture lambda is created once per item id — always read
            // the LATEST index through rememberUpdatedState.
            val currentIndex by rememberUpdatedState(index)
            val isDragging = draggingId == ui.account.id
            // While dragging the card follows the finger ONLY — the list
            // order is untouched until release. No re-sorting mid-drag means
            // no flicker/jitter at all. The card offset is finger travel
            // minus the press point, so the card doesn't jump on touch-down.
            val offsetY = if (isDragging) fingerDelta - pressOffsetY else 0f

            Box(
                modifier = Modifier
                    .then(
                        if (isDragging) {
                            Modifier
                                .zIndex(2f)
                                .offset { IntOffset(0, offsetY.roundToInt()) }
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = { onOpen(ui.account) },
                        onLongClick = null,
                        onLongClickLabel = null
                    )
                    .pointerInput(ui.account.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggingId = ui.account.id
                                dragStartIndex = currentIndex
                                fingerDelta = 0f
                                pressOffsetY = offset.y
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                fingerDelta += amount.y
                            },
                            onDragEnd = {
                                val from = dragStartIndex
                                // slot crossing = card-top travel over the
                                // slot height; round() switches at half a slot
                                val travel = fingerDelta - pressOffsetY
                                val target = (
                                    from + (travel / itemHeightPx).roundToInt()
                                    ).coerceIn(0, displayItems.size - 1)
                                // apply the reorder in the SAME frame as the
                                // offset reset: the card renders at its new
                                // slot with offset 0 — visually continuous.
                                draggingId = null
                                fingerDelta = 0f
                                if (target != from) {
                                    val order = (pendingOrder
                                        ?: displayItems.map { it.account.id }).toMutableList()
                                    val moved = order.removeAt(from)
                                    order.add(target, moved)
                                    pendingOrder = order
                                    onReorder(order)
                                }
                            },
                            onDragCancel = {
                                draggingId = null
                                fingerDelta = 0f
                            }
                        )
                    }
            ) {
                CodeCard(
                    ui = ui,
                    onCopyCode = { onCopyCode(ui) },
                    modifier = Modifier.height(CardHeightPx)
                )
            }
        }
        item {
            if (displayItems.size > 1) {
                Text(
                    text = stringResource(R.string.sorting_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

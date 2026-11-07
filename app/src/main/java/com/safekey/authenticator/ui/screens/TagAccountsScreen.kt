package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.tags.TagBatch
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.TagColorDot

/**
 * Which accounts belong to one tag — reached from the tag list, not from the
 * account list.
 *
 * Every account is listed with a checkbox pre-ticked for its current members, so
 * classifying a batch is a handful of ticks and one save. What stays ticked is
 * linked, what is unticked is unlinked; the write is a single transaction.
 */
@Composable
fun TagAccountsScreen(vm: MainViewModel, tagId: String, onBack: () -> Unit) {
    val tags by vm.tags.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val tag = tags.firstOrNull { it.id == tagId }
    val context = LocalContext.current
    var selected by remember(tagId) { mutableStateOf(emptySet<String>()) }
    var initialized by remember(tagId) { mutableStateOf(false) }

    // Seed the ticks from the tag's current members. The account feed is empty
    // until the database has been decrypted, so wait for the first batch instead
    // of latching an empty selection.
    LaunchedEffect(tagId, accounts.isNotEmpty()) {
        if (!initialized && accounts.isNotEmpty()) {
            selected = accounts
                .filter { account -> account.tags.any { it.id == tagId } }
                .map { it.id }
                .toSet()
            initialized = true
        }
    }
    // Accounts can be deleted while this page is open; never keep a stale tick.
    LaunchedEffect(accounts.size) {
        if (initialized) selected = TagBatch.retainExisting(selected, accounts.map { it.id })
    }

    val allIds = accounts.map { it.id }
    val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = tag?.name ?: stringResource(R.string.tags_title),
                onBack = onBack,
                actions = {
                    IconButtonCompat(
                        icon = if (allSelected) AppIcons.Close else AppIcons.Check,
                        contentDescription = stringResource(
                            if (allSelected) R.string.select_clear else R.string.select_all
                        ),
                        onClick = { selected = TagBatch.toggleAll(selected, allIds) }
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.tag_accounts_hint, tag?.name.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (accounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                        items(accounts, key = { it.id }) { account ->
                            AccountPickRow(
                                account = account,
                                currentTagId = tagId,
                                checked = account.id in selected,
                                onToggle = { selected = TagBatch.toggle(selected, account.id) }
                            )
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tag_accounts_count, selected.size, accounts.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = tag != null && accounts.isNotEmpty(),
                        onClick = {
                            vm.setTagMembers(tagId, selected)
                            vm.showToast(context.getString(R.string.tag_accounts_saved, tag?.name.orEmpty()))
                            onBack()
                        }
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/** One pickable account: checkbox, identity, and the other tags it already has. */
@Composable
private fun AccountPickRow(
    account: Account,
    currentTagId: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val otherTags = account.tags.filter { it.id != currentTagId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The row handles the click, so the box is display-only.
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(
                text = account.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = account.displaySubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (otherTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    otherTags.take(3).forEach { other ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TagColorDot(other.color, Modifier.size(8.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = other.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (otherTags.size > 3) {
                        Text(
                            text = "+${otherTags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

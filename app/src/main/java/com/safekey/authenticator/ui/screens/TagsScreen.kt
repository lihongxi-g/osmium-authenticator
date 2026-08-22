package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.tags.TagColor
import com.safekey.authenticator.tags.TagRules
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val tags by vm.tags.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.tags_title), onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tags_create))
            }
            if (tags.isEmpty()) {
                Text(stringResource(R.string.tags_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        val count = accounts.count { account -> account.tags.any { it.id == tag.id } }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TagDot(tag.color)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(tag.name, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.tag_account_count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { editing = tag }) { Icon(AppIcons.Edit, stringResource(R.string.tags_edit)) }
                                IconButton(onClick = { deleting = tag }) { Icon(AppIcons.Delete, stringResource(R.string.tags_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
    TagEditorDialog(editing ?: if (creating) Tag("", "", TagColor.BLUE, 0, 0) else null, onDismiss = { editing = null; creating = false }) { tag, name, color ->
        if (tag.id.isEmpty()) vm.createTag(name, color) else vm.updateTag(tag, name, color)
        editing = null; creating = false
    }
    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tags_delete_title)) },
            text = { Text(stringResource(R.string.tags_delete_message, tag.name)) },
            confirmButton = { TextButton(onClick = { vm.deleteTag(tag); deleting = null }) { Text(stringResource(R.string.tags_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagEditorDialog(tag: Tag?, onDismiss: () -> Unit, onSave: (Tag, String, String) -> Unit) {
    if (tag == null) return
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    var color by remember(tag.id) { mutableStateOf(tag.color) }
    var error by remember(tag.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.id.isEmpty()) stringResource(R.string.tags_create) else stringResource(R.string.tags_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = false }, label = { Text(stringResource(R.string.tag_name)) }, singleLine = true, isError = error)
                Text(stringResource(R.string.tag_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagColor.ALL.forEach { value -> FilterChip(selected = color == value, onClick = { color = value }, label = { Text(value) }) }
                }
                if (error) Text(stringResource(R.string.tag_name_invalid), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { if (TagRules.isValidName(name)) onSave(tag, name, color) else error = true }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun TagDot(color: String, modifier: Modifier = Modifier) {
    val c = when (color) { TagColor.RED -> androidx.compose.ui.graphics.Color(0xFFBA1A1A); TagColor.ORANGE -> androidx.compose.ui.graphics.Color(0xFF9A4500); TagColor.YELLOW -> androidx.compose.ui.graphics.Color(0xFF806000); TagColor.GREEN -> androidx.compose.ui.graphics.Color(0xFF006D3B); TagColor.CYAN -> androidx.compose.ui.graphics.Color(0xFF006874); TagColor.PURPLE -> androidx.compose.ui.graphics.Color(0xFF6750A4); else -> MaterialTheme.colorScheme.primary }
    androidx.compose.foundation.layout.Spacer(modifier.size(12.dp).clip(CircleShape).background(c))
}

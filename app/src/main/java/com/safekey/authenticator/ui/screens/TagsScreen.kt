package com.safekey.authenticator.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.tags.TagColor
import com.safekey.authenticator.tags.TagRules
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.TagColorDot

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
                                TagColorDot(tag.color, Modifier.size(12.dp))
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
    var nameError by remember(tag.id) { mutableStateOf(false) }
    var colorError by remember(tag.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.id.isEmpty()) stringResource(R.string.tags_create) else stringResource(R.string.tags_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; nameError = false }, label = { Text(stringResource(R.string.tag_name)) }, singleLine = true, isError = nameError, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.tag_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagColor.PALETTE.forEach { value ->
                        FilterChip(
                            selected = color == value,
                            onClick = { color = value; colorError = false },
                            label = { TagColorDot(value, Modifier.size(18.dp)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = if (color in TagColor.ALL) "" else color,
                    onValueChange = { color = it.uppercase(); colorError = false },
                    label = { Text(stringResource(R.string.tag_custom_color)) },
                    placeholder = { Text("#3F51B5") },
                    singleLine = true,
                    isError = colorError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.tag_custom_color_hint)) }
                )
                if (nameError || colorError) Text(
                    stringResource(
                        if (colorError) {
                            R.string.tag_custom_color_invalid
                        } else {
                            R.string.tag_name_invalid
                        }
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    colorError = color.isNotBlank() && !TagColor.isValid(color)
                    nameError = !TagRules.isValidName(name)
                    if (TagRules.isValidName(name) && (color.isBlank() || TagColor.isValid(color))) {
                        onSave(tag, name, if (color.isBlank()) tag.color else color)
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

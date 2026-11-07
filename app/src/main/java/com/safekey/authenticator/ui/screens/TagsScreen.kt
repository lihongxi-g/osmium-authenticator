package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
                                    Text(
                                        stringResource(R.string.tag_account_count, count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

    val draft = editing ?: if (creating) Tag("", "", TagColor.BLUE, 0, 0) else null
    TagEditorDialog(
        tag = draft,
        // Names taken by the *other* tags. The editor rejects a duplicate here,
        // before the repository's require() guard can throw.
        takenNames = tags.filter { it.id != draft?.id }.map { it.name },
        onDismiss = { editing = null; creating = false },
        onSave = { tag, name, color ->
            if (tag.id.isEmpty()) vm.createTag(name, color) else vm.updateTag(tag, name, color)
            editing = null; creating = false
        }
    )

    deleting?.let { tag ->
        val count = accounts.count { account -> account.tags.any { it.id == tag.id } }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tags_delete_title)) },
            text = { Text(stringResource(R.string.tags_delete_message, tag.name, count)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteTag(tag); deleting = null }) {
                    Text(stringResource(R.string.tags_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagEditorDialog(
    tag: Tag?,
    takenNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (Tag, String, String) -> Unit
) {
    if (tag == null) return
    // Presets stored before the swatch grid existed (e.g. "blue") are shown as
    // their hex value so the grid always has a visible selection.
    var name by remember(tag.id) { mutableStateOf(tag.name) }
    var color by remember(tag.id) { mutableStateOf(TagColor.hexFor(tag.color)) }
    var nameErrorRes by remember(tag.id) { mutableStateOf<Int?>(null) }
    var colorError by remember(tag.id) { mutableStateOf(false) }
    val selectedSwatch = TagColor.paletteEntryFor(color)
    val previewHex = if (TagColor.isValid(color)) TagColor.hexFor(color) else TagColor.hexFor(TagColor.BLUE)

    fun validate(): Boolean {
        val clean = TagRules.normalizeName(name)
        nameErrorRes = when {
            !TagRules.isValidName(clean) -> R.string.tag_name_invalid
            TagRules.isDuplicate(clean, takenNames) -> R.string.tag_name_duplicate
            else -> null
        }
        colorError = !TagColor.isValid(color)
        return nameErrorRes == null && !colorError
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.id.isEmpty()) stringResource(R.string.tags_create) else stringResource(R.string.tags_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live preview of the row this tag will get in the account list.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagColorDot(previewHex, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = name.trim().ifBlank { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = previewHex,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameErrorRes = null },
                    label = { Text(stringResource(R.string.tag_name)) },
                    singleLine = true,
                    isError = nameErrorRes != null,
                    supportingText = { nameErrorRes?.let { res -> Text(stringResource(res)) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.tag_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TagColor.PALETTE.forEach { value ->
                        PaletteSwatch(
                            value = value,
                            selected = value.equals(selectedSwatch, ignoreCase = true),
                            onClick = { color = value; colorError = false }
                        )
                    }
                }
                OutlinedTextField(
                    value = color,
                    onValueChange = { raw ->
                        // Hex digits only, so a single stray character cannot
                        // leave the field invalid halfway through typing.
                        val filtered = raw.uppercase().filter { it == '#' || it in "0123456789ABCDEF" }.take(7)
                        color = if (filtered.isEmpty() || filtered.startsWith("#")) filtered else "#$filtered"
                        colorError = false
                    },
                    label = { Text(stringResource(R.string.tag_custom_color)) },
                    placeholder = { Text("#3F51B5") },
                    singleLine = true,
                    isError = colorError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            stringResource(
                                if (colorError) R.string.tag_custom_color_invalid else R.string.tag_custom_color_hint
                            )
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (validate()) onSave(tag, TagRules.normalizeName(name), color)
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** One preset swatch: a 36dp circle, ringed with the primary color when picked. */
@Composable
private fun PaletteSwatch(value: String, selected: Boolean, onClick: () -> Unit) {
    val fill = TagColor.toComposeColor(value)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                }
            )
            .clickable { onClick() }
            .semantics { contentDescription = value },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = if (fill.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

package com.safekey.authenticator.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.model.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(tags: List<Tag>, selected: Set<String>, uncategorized: Boolean, vm: MainViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected.isEmpty() && !uncategorized, onClick = { vm.clearTagFilter() }, label = { Text("All") })
        FilterChip(selected = uncategorized, onClick = { vm.setUncategorizedSelected(!uncategorized) }, label = { Text("Uncategorized") })
        tags.forEach { tag ->
            FilterChip(selected = tag.id in selected, onClick = { vm.toggleTagFilter(tag.id) }, label = { Text(tag.name) })
        }
    }
}

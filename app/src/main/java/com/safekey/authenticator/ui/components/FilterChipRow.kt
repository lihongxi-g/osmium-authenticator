package com.safekey.authenticator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.tags.TagColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipRow(
    tags: List<Tag>,
    selected: Set<String>,
    uncategorized: Boolean,
    vm: MainViewModel,
    scrollState: ScrollState = rememberScrollState()
) {
    if (tags.isEmpty()) return
    Row(
        Modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected.isEmpty() && !uncategorized,
            onClick = { vm.clearTagFilter() },
            label = { Text(stringResource(R.string.filter_all)) }
        )
        FilterChip(
            selected = uncategorized,
            onClick = { vm.setUncategorizedSelected(!uncategorized) },
            label = { Text(stringResource(R.string.filter_uncategorized)) }
        )
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in selected,
                onClick = { vm.toggleTagFilter(tag.id) },
                label = { Text(tag.name) },
                leadingIcon = { TagColorDot(tag.color) }
            )
        }
    }
}

@Composable
fun TagColorDot(color: String, modifier: Modifier = Modifier.size(10.dp)) {
    Spacer(
        modifier
            .clip(CircleShape)
            .background(TagColor.toComposeColor(color))
    )
}

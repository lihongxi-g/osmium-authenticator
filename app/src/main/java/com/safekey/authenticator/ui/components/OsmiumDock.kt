package com.safekey.authenticator.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OsmiumDock(modifier: Modifier = Modifier, selected: OsmiumDockTab, onSelected: (OsmiumDockTab) -> Unit) {
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DockItem(OsmiumDockTab.Authenticator, selected == OsmiumDockTab.Authenticator, onSelected)
            DockItem(OsmiumDockTab.Link, selected == OsmiumDockTab.Link, onSelected)
        }
    }
}

enum class OsmiumDockTab { Authenticator, Link }

@Composable
private fun DockItem(tab: OsmiumDockTab, selected: Boolean, onSelected: (OsmiumDockTab) -> Unit) {
    val width by animateDpAsState(if (selected) 112.dp else 48.dp, tween(360, easing = FastOutSlowInEasing), label = "dock-width")
    Surface(onClick = { onSelected(tab) }, shape = RoundedCornerShape(22.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, modifier = Modifier.width(width)) {
        Text(if (selected) if (tab == OsmiumDockTab.Authenticator) "Authenticator" else "Osmium Link" else if (tab == OsmiumDockTab.Authenticator) "76" else "↔", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.bilibili.watchlater.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bilibili.watchlater.ui.theme.LocalPageBackdrop
import com.bilibili.watchlater.ui.theme.glass.*

@Composable
fun GlassNavigationBar(selected: Int, labels: List<String>, icons: List<ImageVector>, onSelect: (Int) -> Unit,
                       modifier: Modifier = Modifier) {
    require(labels.isNotEmpty() && labels.size == icons.size && selected in labels.indices)
    val backdrop = LocalPageBackdrop.current ?: return
    LiquidBottomTabs({ selected }, onSelect, backdrop, labels.size, modifier.selectableGroup()) {
        val replica = LocalLiquidTabReplica.current
        val replicaInk = LocalLiquidTabInk.current
        val magnification = LocalLiquidBottomTabScale.current
        labels.forEachIndexed { index, label ->
            val interaction = remember { MutableInteractionSource() }
            val ink = if (replica) replicaInk else MaterialTheme.colorScheme.onSurface
            // Only the real row accepts taps or exposes accessibility nodes. The
            // second row is an offscreen optical input, never a duplicate control.
            Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                .then(if (replica) Modifier else Modifier.selectable(selected == index,
                    interactionSource = interaction, indication = null, role = Role.Tab,
                    onClick = { onSelect(index) }))
                .graphicsLayer { scaleX = magnification(); scaleY = magnification() }
                .padding(horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                Icon(icons[index], null, Modifier.size(24.dp), tint = ink)
                Text(label, color = ink, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

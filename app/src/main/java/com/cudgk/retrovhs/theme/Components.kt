package com.cudgk.retrovhs.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * High-contrast neon chip used throughout the app. Selected = filled neon accent;
 * unselected = panel surface with neon outline.
 */
@Composable
fun NeonChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val bg = if (selected) accent else InkPanel
    val fg = if (selected) InkBlack else InkText
    val border = if (selected) accent else InkBorder
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = if (selected) 1f else alpha))
            .border(1.dp, border.copy(alpha = alpha), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg.copy(alpha = if (enabled) 1f else 0.6f),
        )
    }
}

@Composable
fun NeonChipRow(
    items: List<Pair<String, Any>>,
    selectedKey: Any?,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(items.size) { i ->
            val (label, key) = items[i]
            NeonChip(
                label = label,
                selected = selectedKey == key,
                onClick = { onSelect(key) },
                enabled = enabled,
                accent = accent,
            )
        }
    }
}

@Composable
fun ControlRow(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(end = 4.dp),
        )
        content()
    }
}

/** Translucent panel with subtle neon top border — used as the bottom controls bar. */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(InkBlack.copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            )
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
    ) {
        content()
    }
}

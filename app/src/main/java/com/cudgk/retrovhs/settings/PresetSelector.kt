package com.cudgk.retrovhs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PresetRow(
    selected: String?,
    enabled: Boolean = true,
    onSelect: (Preset) -> Unit,
    modifier: Modifier = Modifier,
    chipBg: Color = Color.DarkGray,
    chipFg: Color = Color.White,
    chipBgSelected: Color = Color.White,
    chipFgSelected: Color = Color.Black,
    onlyEngine: Engine? = null,
) {
    val items = if (onlyEngine == null) Presets.ALL else Presets.ALL.filter { it.engine == onlyEngine }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(items.size) { i ->
            val p = items[i]
            val isSel = p.name == selected
            AssistChip(
                enabled = enabled,
                onClick = { onSelect(p) },
                label = { Text(p.name) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSel) chipBgSelected else chipBg,
                    labelColor = if (isSel) chipFgSelected else chipFg,
                ),
            )
        }
    }
}

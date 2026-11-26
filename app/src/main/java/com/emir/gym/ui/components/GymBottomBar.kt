package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii

/** Alt gezinme öğesi. */
@Immutable
data class GymNavItem(val label: String, val icon: ImageVector)

/**
 * Minimal alt gezinme çubuğu: siyah zemin, üstte ince çizgi, seçili sekmede
 * vurgu renginde ikon ve üstünde kısa çubuk. Sistem gezinme alanı kadar iç boşluk ekler.
 */
@Composable
fun GymBottomBar(
    items: List<GymNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GymTheme.colors
    Column(modifier.fillMaxWidth().background(c.barBackground)) {
        HorizontalDivider(thickness = 1.dp, color = c.cardBorder)
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { i, item ->
                BarItem(item, selected = i == selectedIndex, onClick = { onSelect(i) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarItem(item: GymNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .height(64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 36.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(20.dp)
                .height(2.dp)
                .clip(Radii.pill)
                .background(if (selected) c.accent else Color.Transparent),
        )
        Spacer(Modifier.height(10.dp))
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) c.accent else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Uygulamanın dört sekmesi için hazır öğeler (metinler strings_spor.xml'den). */
@Composable
fun defaultNavItems(): List<GymNavItem> = listOf(
    GymNavItem(stringResource(R.string.tab_daily), GymIcons.Today),
    GymNavItem(stringResource(R.string.tab_weekly), GymIcons.Week),
    GymNavItem(stringResource(R.string.tab_progress), GymIcons.Trend),
    GymNavItem(stringResource(R.string.tab_settings), GymIcons.Sliders),
)

@PreviewLightDark
@Composable
private fun GymBottomBarPreview() = PreviewBox {
    var sel by remember { mutableIntStateOf(0) }
    GymBottomBar(defaultNavItems(), sel, { sel = it })
}

package com.emir.gym.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii

/** Ana eylem: dolu vurgu rengi, hap biçimi, 52 dp yükseklik. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = Radii.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accentFill,
            contentColor = c.onAccentFill,
            disabledContainerColor = c.track,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) { ButtonContent(text, icon) }
}

/** İkincil eylem: şeffaf zemin, ince çerçeve. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = Radii.pill,
        border = BorderStroke(1.dp, if (enabled) c.stroke else c.cardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = scheme.onSurface,
            disabledContentColor = scheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) { ButtonContent(text, icon) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize))
}

/**
 * Yatay kaydırılabilir tek seçimli çip dizisi (ör. kas grubu, zaman aralığı).
 * Seçili çip ters renkle (açık zemin, koyu metin) gösterilir.
 */
@Composable
fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(contentPadding.calculateLeftPadding(LayoutDirection.Ltr)))
        options.forEachIndexed { i, label ->
            GymChip(label = label, selected = i == selectedIndex, onClick = { onSelect(i) })
        }
        Spacer(Modifier.width(contentPadding.calculateRightPadding(LayoutDirection.Ltr)))
    }
}

@Composable
fun GymChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val c = GymTheme.colors
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = Radii.pill,
        color = if (selected) scheme.onSurface else Color.Transparent,
        contentColor = if (selected) scheme.surface else scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, c.stroke),
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@PreviewLightDark
@Composable
private fun ButtonsPreview() = PreviewBox {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Antrenmana başla", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = GymIcons.Play)
        SecondaryButton("Önceki antrenmanı kopyala", onClick = {}, modifier = Modifier.fillMaxWidth())
        PrimaryButton("Kaydet", onClick = {}, enabled = false)
        var sel by remember { mutableIntStateOf(0) }
        ChipRow(listOf("Tümü", "Göğüs", "Sırt", "Bacak", "Omuz"), sel, { sel = it })
    }
}

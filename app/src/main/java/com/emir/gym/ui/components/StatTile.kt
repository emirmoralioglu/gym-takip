package com.emir.gym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.tabular

/** Delta yönü: renk ve ok buna göre seçilir. */
enum class DeltaTone { Positive, Negative, Neutral }

/**
 * İstatistik kutusu: büyük sayı, birim, etiket ve isteğe bağlı küçük delta.
 *
 * @param value önceden biçimlendirilmiş sayı (ör. UiFormat.grouped(6393) = "6 393").
 * @param unit sayının altındaki küçük birim (ör. "kg").
 * @param label kalın etiket (ör. "Haftalık tonaj").
 * @param caption etiketin altındaki gri açıklama.
 * @param delta küçük değişim metni (ör. "+4.2%"); [deltaTone] rengini belirler.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    delta: String? = null,
    deltaTone: DeltaTone = DeltaTone.Neutral,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier = modifier.semantics(mergeDescendants = true) {}, onClick = onClick) {
        Text(
            value,
            style = NumberStyles.large,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unit != null) {
            Text(unit, style = NumberStyles.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null || delta != null) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (delta != null) DeltaBadge(delta, deltaTone)
                if (caption != null) {
                    Text(
                        caption,
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun DeltaBadge(text: String, tone: DeltaTone, modifier: Modifier = Modifier) {
    val color: Color = when (tone) {
        DeltaTone.Positive -> GymTheme.colors.accent
        DeltaTone.Negative -> MaterialTheme.colorScheme.error
        DeltaTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (tone) {
            DeltaTone.Positive -> Icon(GymIcons.ArrowUp, null, tint = color, modifier = Modifier.size(12.dp))
            DeltaTone.Negative -> Icon(GymIcons.ArrowDown, null, tint = color, modifier = Modifier.size(12.dp))
            DeltaTone.Neutral -> Unit
        }
        if (tone != DeltaTone.Neutral) Spacer(Modifier.width(2.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.tabular(), color = color, maxLines = 1)
    }
}

@PreviewLightDark
@Composable
private fun StatTilePreview() = PreviewBox {
    StatTile(
        value = UiFormat.grouped(6393),
        unit = "kg",
        label = "Haftalık tonaj",
        caption = "≈ ${UiFormat.grouped(6393)} kg",
        modifier = Modifier.width(170.dp),
    )
}

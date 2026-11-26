package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.Spacing

/**
 * Temel kart: yukarıdan aşağı hafif degrade, ince çerçeve, yuvarlak köşe.
 *
 * @param onClick verilirse kart tıklanabilir olur.
 * @param highlighted true ise çerçeve vurgu rengiyle çizilir (ör. bugünkü antrenman).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Spacing.cardPadding),
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = GymTheme.colors
    Column(
        modifier = modifier
            .clip(Radii.card)
            .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
            .border(borderWidth, if (highlighted) c.accent else c.cardBorder, Radii.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@PreviewLightDark
@Composable
private fun AppCardPreview() = PreviewBox {
    AppCard {
        Text("Bugünkü antrenman", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text("Upper A (Makine) · ~50 dk", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

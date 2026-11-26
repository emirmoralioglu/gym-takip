package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.Radii

/**
 * Dinlenme sayacı hapı: küçük halka, kalan süre, +30 sn, duraklat/sürdür, kapat.
 * Süre ve çalışma durumu çağıran tarafta tutulur.
 *
 * @param remainingSeconds kalan süre (saniye).
 * @param totalSeconds başlangıç süresi; halka oranı için.
 */
@Composable
fun RestTimerPill(
    remainingSeconds: Int,
    totalSeconds: Int,
    running: Boolean,
    onToggle: () -> Unit,
    onAddTime: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    val progress = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.pill)
            .background(Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
            .border(1.dp, c.cardBorder, Radii.pill)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(progress = progress, size = 36.dp, strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(R.string.ui_rest_label), style = LabelStyles.capsSmall, color = scheme.onSurfaceVariant)
            Text(UiFormat.clock(remainingSeconds), style = NumberStyles.medium, color = scheme.onSurface)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val addDesc = stringResource(R.string.ui_rest_add)
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(Radii.pill)
                    .clickable(role = Role.Button, onClickLabel = addDesc, onClick = onAddTime)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.ui_rest_add_short),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { this.contentDescription = addDesc },
                )
            }
            RoundIconButton(
                icon = if (running) GymIcons.Pause else GymIcons.Play,
                description = stringResource(if (running) R.string.ui_rest_pause else R.string.ui_rest_resume),
                filled = true,
                onClick = onToggle,
            )
            RoundIconButton(
                icon = GymIcons.Close,
                description = stringResource(R.string.ui_rest_skip),
                filled = false,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, filled: Boolean, onClick: () -> Unit) {
    val c = GymTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) c.accentFill else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (filled) c.onAccentFill else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun RestTimerPillPreview() = PreviewBox {
    // Plan: set arası 90 sn.
    RestTimerPill(remainingSeconds = 90, totalSeconds = 90, running = true, onToggle = {}, onAddTime = {}, onDismiss = {})
}

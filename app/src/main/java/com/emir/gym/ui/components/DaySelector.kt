package com.emir.gym.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles

/** Bir günün antrenman durumu. */
enum class DayState {
    /** Antrenman yapıldı: dolu vurgu dairesi. */
    TAMAMLANDI,

    /** Planlı ama henüz yapılmadı: kesik çizgili halka. */
    PLANLI,

    /** Plan yok: çerçevesiz, gri. */
    DINLENME,

    /** Planlıydı, günü geçti, yapılmadı: düz kırmızı halka. */
    KACIRILDI,
}

/**
 * Gün seçicideki tek gün.
 *
 * @param label kısa gün adı (ör. "PZT").
 * @param isToday bugünse altına vurgu noktası çizilir.
 */
@Immutable
data class DayItem(
    val label: String,
    val state: DayState,
    val isToday: Boolean = false,
)

/**
 * Haftanın 7 günü için yuvarlak seçici. Seçili gün dış halkayla gösterilir.
 * Durum (seçili gün) çağıran tarafta tutulur.
 */
@Composable
fun DaySelector(
    days: List<DayItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEachIndexed { index, day ->
            DayCircle(day = day, selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun DayCircle(day: DayItem, selected: Boolean, onClick: () -> Unit) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    val stateText = when (day.state) {
        DayState.TAMAMLANDI -> stringResource(R.string.ui_day_done)
        DayState.PLANLI -> stringResource(R.string.ui_day_planned)
        DayState.DINLENME -> stringResource(R.string.ui_day_rest)
        DayState.KACIRILDI -> stringResource(R.string.ui_day_missed)
    }
    val todayText = stringResource(R.string.ui_day_today)
    val description = buildString {
        append(day.label).append(", ").append(stateText)
        if (day.isToday) append(", ").append(todayText)
    }

    val fill: Color = when (day.state) {
        DayState.TAMAMLANDI -> c.accentFill
        DayState.DINLENME -> scheme.surfaceContainer
        else -> Color.Transparent
    }
    val textColor: Color = when (day.state) {
        DayState.TAMAMLANDI -> c.onAccentFill
        DayState.PLANLI -> scheme.onSurface
        DayState.DINLENME -> scheme.onSurfaceVariant
        DayState.KACIRILDI -> scheme.error
    }
    val ringColor: Color? = when (day.state) {
        DayState.PLANLI -> c.stroke
        DayState.KACIRILDI -> scheme.error
        else -> null
    }
    val dashed = day.state == DayState.PLANLI
    val selectionColor = scheme.onSurface
    val todayColor = c.accent

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(OuterSize)
                .clip(CircleShape)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = null,
                    indication = ripple(bounded = true),
                )
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val outer = size.minDimension / 2
                val inner = InnerSize.toPx() / 2
                if (selected) {
                    drawCircle(selectionColor, radius = outer - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
                if (fill != Color.Transparent) drawCircle(fill, radius = inner)
                if (ringColor != null) {
                    val sw = 1.5.dp.toPx()
                    drawCircle(
                        ringColor,
                        radius = inner - sw / 2,
                        style = Stroke(
                            width = sw,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                        ),
                    )
                }
            }
            Text(day.label, style = LabelStyles.capsSmall, color = textColor, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.size(5.dp)) {
            if (day.isToday) drawCircle(todayColor)
        }
    }
}

private val OuterSize: Dp = 48.dp
private val InnerSize: Dp = 40.dp

/** Pazartesiden başlayan kısa gün adları (PZT ... PAZ). */
@Composable
fun weekdayShortLabels(): List<String> = stringArrayResource(R.array.ui_weekday_short).toList()

@PreviewLightDark
@Composable
private fun DaySelectorPreview() = PreviewBox {
    val labels = weekdayShortLabels()
    val states = listOf(
        DayState.TAMAMLANDI, DayState.KACIRILDI, DayState.PLANLI, DayState.PLANLI,
        DayState.DINLENME, DayState.PLANLI, DayState.DINLENME,
    )
    var selected by remember { mutableIntStateOf(2) }
    DaySelector(
        days = labels.mapIndexed { i, l -> DayItem(l, states[i], isToday = i == 2) },
        selectedIndex = selected,
        onSelect = { selected = it },
    )
}

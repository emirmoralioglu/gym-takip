package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.tabular
import com.emir.gym.ui.theme.upperTr

/**
 * Makro ilerleme çubuğu (protein, kalori, su...).
 * Hedef aşılırsa çubuk dolu kalır ve değer PR rengine döner.
 *
 * @param unit değerin birimi (ör. "g", "kcal", "L").
 */
@Composable
fun MacroProgress(
    label: String,
    current: Double,
    target: Double,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = GymTheme.colors.accent,
) {
    val scheme = MaterialTheme.colorScheme
    val c = GymTheme.colors
    val progress = if (target <= 0.0) 0f else (current / target).toFloat().coerceIn(0f, 1f)
    val reached = target > 0.0 && current >= target
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                label.upperTr(),
                style = LabelStyles.caps,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                UiFormat.number(current, 1),
                style = NumberStyles.small,
                color = if (reached) c.pr else scheme.onSurface,
            )
            Text(
                " / ${UiFormat.number(target, 1)} $unit",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(Radii.pill)
                .background(c.track),
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(Radii.pill)
                        .background(if (reached) c.pr else color),
                )
            }
        }
    }
}

/**
 * Öğün satırı: saat, ad, ayrıntı ve isteğe bağlı işaret.
 *
 * @param time "08:30" gibi saat; null ise sütun boş kalır.
 * @param detail ör. "Yulaf, süt, muz".
 * @param value sağdaki kısa değer (ör. "420 kcal").
 * @param onDoneChange verilirse satır işaretlenebilir olur.
 */
@Composable
fun MealRow(
    title: String,
    modifier: Modifier = Modifier,
    time: String? = null,
    detail: String? = null,
    value: String? = null,
    done: Boolean = false,
    onDoneChange: ((Boolean) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val c = GymTheme.colors
    val base = modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radii.inner)
    val rowModifier = if (onDoneChange != null) {
        base.toggleable(value = done, role = Role.Checkbox, onValueChange = onDoneChange)
    } else base
    Row(
        rowModifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            time ?: "",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge.tabular(), color = scheme.onSurface, maxLines = 1)
        }
        if (onDoneChange != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (done) c.accentFill else Color.Transparent)
                    .border(1.5.dp, if (done) c.accentFill else c.stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(GymIcons.Check, stringResource(R.string.ui_meal_done), tint = c.onAccentFill, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NutritionPreview() = PreviewBox {
    // Yer tutucu değerler; gerçek hedef ya da öğün verisi değildir.
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MacroProgress("Protein", current = 60.0, target = 120.0, unit = "g")
        MacroProgress("Su", current = 2.0, target = 2.0, unit = "L")
        var done by remember { mutableStateOf(true) }
        MealRow(title = "Kahvaltı", time = "--:--", detail = "Öğün içeriği", done = done, onDoneChange = { done = it })
    }
}

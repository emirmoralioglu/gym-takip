package com.emir.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.Meal
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.MacroProgress
import com.emir.gym.ui.components.SecondaryButton
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.tabular
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate

/** Onceki/sonraki gun oklari ve tarih basligi. Basliga dokunmak tarih secicisini acar. */
@Composable
fun DayNavigator(
    date: LocalDate,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    val canNext = NutritionUi.canGoNext(date.toEpochDay(), today.toEpochDay())
    val scheme = MaterialTheme.colorScheme
    val title = when (NutritionUi.dayLabel(date.toEpochDay(), today.toEpochDay())) {
        NutritionUi.DayLabel.TODAY -> stringResource(R.string.nutr_today)
        NutritionUi.DayLabel.YESTERDAY -> stringResource(R.string.nutr_yesterday)
        NutritionUi.DayLabel.DATE -> date.format(DateFmt)
    }
    AppCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(GymIcons.ChevronRight, stringResource(R.string.nutr_prev_day), Modifier.rotate(180f), tint = scheme.onSurface)
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(Radii.inner)
                    .clickable(onClickLabel = stringResource(R.string.nutr_pick_date), onClick = onPick)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1)
                Text(dateWithWeekday(date), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onNext, enabled = canNext) {
                Icon(
                    GymIcons.ChevronRight,
                    stringResource(R.string.nutr_next_day),
                    Modifier.alpha(if (canNext) 1f else 0.3f),
                    tint = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(status: NutritionUi.Status, unit: String, protein: Boolean = false, hasRange: Boolean = false) {
    val c = GymTheme.colors
    val text = when (status.kind) {
        NutritionUi.StatusKind.REMAINING -> stringResource(R.string.nutr_remaining, if (unit == "ml") UiFormat.number(status.amount, 0) else NutritionUi.grams(status.amount), unit)
        NutritionUi.StatusKind.REACHED -> stringResource(if (hasRange) R.string.nutr_in_range else R.string.nutr_reached)
        NutritionUi.StatusKind.OVER ->
            if (protein) stringResource(R.string.nutr_over_protein, NutritionUi.grams(status.amount))
            else stringResource(R.string.nutr_over, if (unit == "ml") UiFormat.number(status.amount, 0) else NutritionUi.grams(status.amount), unit)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (status.kind == NutritionUi.StatusKind.REMAINING) MaterialTheme.colorScheme.onSurfaceVariant else c.pr,
    )
}

/** Protein, kalori ve su ozeti. Hedefi olmayan satir yerine yonlendirme gosterilir. */
@Composable
fun SummaryCard(
    proteinG: Double,
    calorieTotal: NutritionUi.CalorieTotal,
    waterMl: Int,
    proteinRange: ClosedFloatingPointRange<Double>?,
    weightKg: Double?,
    calorieGoal: Int?,
    waterGoal: NutritionUi.WaterGoal,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val g = stringResource(R.string.nutr_unit_g)
    AppCard(Modifier.fillMaxWidth()) {
        // Protein
        if (proteinRange != null) {
            MacroProgress(stringResource(R.string.nutr_protein), proteinG, proteinRange.start, g)
            Spacer(Modifier.height(6.dp))
            StatusLine(NutritionUi.status(proteinG, proteinRange.start, proteinRange.endInclusive), g, protein = true, hasRange = true)
            Text(
                stringResource(
                    R.string.nutr_protein_range,
                    NutritionUi.grams(proteinRange.start), NutritionUi.grams(proteinRange.endInclusive), NutritionUi.grams(weightKg ?: 0.0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.nutr_protein).upperTr(), style = LabelStyles.caps, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.nutr_taken_protein, NutritionUi.grams(proteinG)), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.nutr_no_weight_title), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            Text(stringResource(R.string.nutr_no_weight_text), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(stringResource(R.string.nutr_add_weight), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.nutr_protein_tip), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)

        Spacer(Modifier.height(18.dp))
        // Kalori
        val kcalUnit = stringResource(R.string.nutr_unit_kcal)
        if (calorieGoal != null && calorieGoal > 0) {
            if (calorieTotal.hasAny) {
                MacroProgress(stringResource(R.string.nutr_calories), calorieTotal.kcal.toDouble(), calorieGoal.toDouble(), kcalUnit)
                Spacer(Modifier.height(6.dp))
                StatusLine(NutritionUi.status(calorieTotal.kcal.toDouble(), calorieGoal.toDouble()), kcalUnit)
            } else {
                // Hic kalori girilmediyse bos cubuk "hedefin cok altindasin" gibi yanilticidir.
                Text(stringResource(R.string.nutr_calories).upperTr(), style = LabelStyles.caps, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.nutr_cal_none_goal, UiFormat.grouped(calorieGoal.toLong())), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                Text(stringResource(R.string.nutr_cal_none_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        } else {
            Text(stringResource(R.string.nutr_calories).upperTr(), style = LabelStyles.caps, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                if (calorieTotal.hasAny) stringResource(R.string.nutr_cal_taken, UiFormat.grouped(calorieTotal.kcal.toLong())) else stringResource(R.string.nutr_cal_taken_none),
                style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
            )
            Text(stringResource(R.string.nutr_cal_no_goal), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            TextButton(onClick = onOpenSettings, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.nutr_open_settings), color = GymTheme.colors.accent)
            }
        }
        if (calorieTotal.hasMissing) {
            Text(stringResource(R.string.nutr_cal_missing_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(18.dp))
        // Su
        MacroProgress(stringResource(R.string.nutr_water), waterMl.toDouble(), waterGoal.totalMl.toDouble(), stringResource(R.string.nutr_unit_ml))
        Spacer(Modifier.height(6.dp))
        StatusLine(NutritionUi.status(waterMl.toDouble(), waterGoal.totalMl.toDouble()), stringResource(R.string.nutr_unit_ml))
        if (waterGoal.bonusMl > 0) {
            Text(
                stringResource(R.string.nutr_water_bonus, waterGoal.bonusMl.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Ogun satiri: dokununca duzenler, sagdaki cop kutusu siler. */
@Composable
fun MealItem(meal: Meal, onEdit: () -> Unit, onDelete: () -> Unit) {
    val parts = buildList {
        if (meal.calories > 0) add(stringResource(R.string.nutr_meal_detail, UiFormat.grouped(meal.calories.toLong())))
        meal.carbsG?.let { add(stringResource(R.string.nutr_carbs_short, NutritionUi.grams(it))) }
        meal.fatG?.let { add(stringResource(R.string.nutr_fat_short, NutritionUi.grams(it))) }
    }
    val title = meal.description.ifBlank { meal.mealType.label }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // components.MealRow'un sabit saat sutunu burada bos kalirdi; sade, solu hizali satir.
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).clip(Radii.inner).clickable(onClick = onEdit).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                stringResource(R.string.nutr_group_value, NutritionUi.grams(meal.proteinG)),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        DeleteButton(stringResource(R.string.nutr_delete_desc, meal.description.ifBlank { meal.mealType.label }), onDelete)
    }
}

@Composable
fun DeleteButton(description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Delete, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Son 7 gunun protein cubuklari; hedef alt siniri varsa kesikli cizgi. */
@Composable
fun WeekProteinCard(week: NutritionUi.WeekProtein, targetLow: Double?) {
    val scheme = MaterialTheme.colorScheme
    val c = GymTheme.colors
    val labels = stringArrayResource(R.array.ui_weekday_short)
    val avgText = week.averageG?.let { NutritionUi.grams(it) } ?: "0"
    val maxDay = week.days.maxOf { it.proteinG }
    val scale = maxOf(maxDay, targetLow ?: 0.0, 1.0)
    val barArea = 84.dp
    val valueArea = 18.dp
    val chartDesc = stringResource(R.string.nutr_week_chart_desc, avgText)
    AppCard(
        Modifier.fillMaxWidth().semantics { contentDescription = chartDesc },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nutr_week_title), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
            Text(stringResource(R.string.nutr_week_avg, avgText), style = MaterialTheme.typography.labelMedium.tabular(), color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(barArea + valueArea)) {
            Row(Modifier.fillMaxWidth().height(barArea + valueArea), verticalAlignment = Alignment.Bottom) {
                week.days.forEachIndexed { i, d ->
                    val last = i == week.days.lastIndex
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (d.proteinG > 0) {
                            Text(
                                NutritionUi.grams(d.proteinG),
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.height(valueArea),
                            )
                        }
                        val h = if (d.proteinG > 0) (barArea * (d.proteinG / scale).toFloat()).coerceAtLeast(3.dp) else 2.dp
                        Box(
                            Modifier
                                .width(18.dp)
                                .height(h)
                                .clip(Radii.pill)
                                .background(if (d.proteinG <= 0) c.track else if (last) c.accent else c.accent.copy(alpha = 0.55f)),
                        )
                    }
                }
            }
            if (targetLow != null) {
                val lineColor = c.pr
                Canvas(Modifier.fillMaxWidth().height(barArea + valueArea)) {
                    val y = size.height - (barArea.toPx() * (targetLow / scale).toFloat())
                    drawLine(
                        lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            week.days.forEach { d ->
                val wd = LocalDate.ofEpochDay(d.epochDay).dayOfWeek.value
                Text(
                    labels[wd - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (targetLow != null) {
            Text(stringResource(R.string.nutr_week_target, NutritionUi.grams(targetLow)), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.nutr_week_avg_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

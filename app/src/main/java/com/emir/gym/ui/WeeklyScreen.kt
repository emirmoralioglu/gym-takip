package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.ActivityType
import com.emir.gym.data.PlanLogic
import com.emir.gym.data.WeeklyStats
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.DeltaBadge
import com.emir.gym.ui.components.DeltaTone
import com.emir.gym.ui.components.LineChartCard
import com.emir.gym.ui.components.ProgressRingCard
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.components.StatTile
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate
import kotlin.math.roundToLong

/** Haftalık sekme: bu hafta özeti, gün bazlı tonaj, geçen haftayla karşılaştırma ve aktivite süreleri. */
@Composable
fun WeeklyScreen(vm: GymViewModel, modifier: Modifier = Modifier) {
    val entries by vm.entries.collectAsState()
    val days by vm.planDays.collectAsState()
    val activities by vm.activities.collectAsState()
    val list = entries
    if (list == null) {
        ScreenColumn(modifier) { Text(stringResource(R.string.screens_loading), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    val today = LocalDate.now()
    val thisStart = WeeklyStats.weekStart(today)
    val lastStart = thisStart.minusWeeks(1)
    val week = remember(today, days, list, activities) { PlanLogic.weekProgress(today, today, days, list, activities) }
    val cur = remember(list, thisStart) { WeeklyStats.summarize(list, thisStart) }
    val prev = remember(list, lastStart) { WeeklyStats.summarize(list, lastStart) }
    val minutes = remember(activities, thisStart) { FolderLogic.activityMinutes(thisStart, activities) }
    val lastMinutes = remember(activities, lastStart) { FolderLogic.activityMinutes(lastStart, activities) }
    val short = stringArrayResource(R.array.ui_weekday_short)

    ScreenColumn(modifier) {
        Text(stringResource(R.string.tab_weekly), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.screens_week_range, thisStart.format(ShortDateFmt), thisStart.plusDays(6).format(ShortDateFmt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressRingCard(
                done = week.doneWeightDays,
                total = week.plannedWeightDays,
                label = stringResource(R.string.screens_this_week),
                caption = stringResource(R.string.screens_workouts_caption),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val dTon = FolderLogic.deltaPercent(cur.totalVolume, prev.totalVolume)
            StatTile(
                value = UiFormat.grouped(cur.totalVolume.roundToLong()),
                unit = stringResource(R.string.screens_kg),
                label = stringResource(R.string.screens_tonnage),
                delta = dTon?.let { stringResource(R.string.screens_delta_percent, if (it > 0) "+$it" else "$it") },
                deltaTone = when {
                    dTon == null || dTon == 0 -> DeltaTone.Neutral
                    dTon > 0 -> DeltaTone.Positive
                    else -> DeltaTone.Negative
                },
                caption = if (dTon == null) stringResource(R.string.screens_no_last_week) else stringResource(R.string.screens_vs_last_week),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = UiFormat.grouped(cur.totalSets.toLong()),
                label = stringResource(R.string.screens_sets_total),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatTile(
                value = UiFormat.grouped(cur.trainingDays.toLong()),
                label = stringResource(R.string.screens_training_days),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        LineChartCard(
            title = stringResource(R.string.screens_daily_tonnage),
            points = cur.dailyVolume.map { it.toFloat() },
            trailing = stringResource(R.string.screens_tonnage_kg, UiFormat.grouped(cur.totalVolume.roundToLong())),
            startLabel = short.first(),
            endLabel = short.last(),
            emptyText = stringResource(R.string.screens_week_empty),
        )

        SectionHeader(stringResource(R.string.screens_compare))
        AppCard(Modifier.fillMaxWidth()) {
            CompareRow(stringResource(R.string.screens_tonnage), cur.totalVolume.roundToLong(), prev.totalVolume.roundToLong(), stringResource(R.string.screens_kg))
            Spacer(Modifier.height(10.dp))
            CompareRow(stringResource(R.string.screens_sets_total), cur.totalSets.toLong(), prev.totalSets.toLong(), null)
            Spacer(Modifier.height(10.dp))
            CompareRow(stringResource(R.string.screens_training_days), cur.trainingDays.toLong(), prev.trainingDays.toLong(), null)
        }

        SectionHeader(stringResource(R.string.screens_activity_week))
        AppCard(Modifier.fillMaxWidth()) {
            ActivityType.entries.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.height(10.dp))
                CompareRow(activityLabel(t), (minutes[t] ?: 0).toLong(), (lastMinutes[t] ?: 0).toLong(), stringResource(R.string.screens_unit_min))
            }
        }
    }
}

/** "Etiket   bu hafta unit   (geçen hafta X)" satırı. */
@Composable
private fun CompareRow(label: String, current: Long, previous: Long, unit: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label.upperTr(), style = LabelStyles.caps, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.screens_last_week_value, UiFormat.grouped(previous) + (unit?.let { " $it" } ?: "")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                UiFormat.grouped(current) + (unit?.let { " $it" } ?: ""),
                style = NumberStyles.small,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            FolderLogic.deltaPercent(current.toDouble(), previous.toDouble())?.let { d ->
                DeltaBadge(
                    stringResource(R.string.screens_delta_percent, if (d > 0) "+$d" else "$d"),
                    when {
                        d > 0 -> DeltaTone.Positive
                        d < 0 -> DeltaTone.Negative
                        else -> DeltaTone.Neutral
                    },
                )
            }
        }
    }
}

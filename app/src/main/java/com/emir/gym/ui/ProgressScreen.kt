package com.emir.gym.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.ChartRange
import com.emir.gym.data.ExerciseRecord
import com.emir.gym.data.ProgressChart
import com.emir.gym.data.Records
import com.emir.gym.data.WorkoutEntry
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.ChipRow
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.LineChartCard
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate

/** İlerleme sekmesi: Rekorlar ve Gelişim (egzersiz grafiği). */
@Composable
fun ProgressScreen(vm: GymViewModel, modifier: Modifier = Modifier) {
    val entries by vm.entries.collectAsState()
    val list = entries
    var section by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn(modifier) {
        Text(stringResource(R.string.tab_progress), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        if (list == null) {
            Text(stringResource(R.string.screens_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScreenColumn
        }
        ChipRow(
            listOf(stringResource(R.string.progress_records), stringResource(R.string.progress_chart)),
            selectedIndex = section,
            onSelect = { section = it },
        )
        if (section == 0) RecordsSection(list) { name -> selected = name; section = 1 }
        else ChartSection(list, selected) { selected = it }
    }
}

@Composable
private fun RecordsSection(entries: List<WorkoutEntry>, onOpen: (String) -> Unit) {
    val records = remember(entries) { Records.compute(entries) }
    if (records.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_records_empty_title),
            message = stringResource(R.string.records_empty),
            icon = GymIcons.Trophy,
        )
        return
    }
    records.forEach { r -> RecordCard(r) { onOpen(r.exercise) } }
    Text(stringResource(R.string.records_epley_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RecordCard(r: ExerciseRecord, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(r.exercise, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            RecordValue(
                stringResource(R.string.records_best_weight),
                stringResource(R.string.screens_kg_value, UiFormat.number(r.maxWeightKg)),
                LocalDate.ofEpochDay(r.maxWeightDay).format(DateFmt),
                Modifier.weight(1f),
            )
            if (r.best1Rm != null && r.best1RmDay != null) {
                RecordValue(
                    stringResource(R.string.records_best_1rm),
                    stringResource(R.string.screens_kg_value, UiFormat.number(r.best1Rm, 1)),
                    LocalDate.ofEpochDay(r.best1RmDay).format(DateFmt),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecordValue(label: String, value: String, date: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.upperTr(), style = LabelStyles.caps, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = NumberStyles.medium, color = GymTheme.colors.accent, maxLines = 1)
        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChartSection(entries: List<WorkoutEntry>, selected: String?, onSelect: (String) -> Unit) {
    val names = remember(entries) { ProgressChart.exerciseNames(entries) }
    if (names.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_chart_empty_title),
            message = stringResource(R.string.chart_no_exercise),
            icon = GymIcons.Trend,
        )
        return
    }
    val current = names.firstOrNull { it.equals(selected, ignoreCase = true) } ?: names.first()
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    val ranges = ChartRange.entries

    Text(stringResource(R.string.chart_pick_exercise).upperTr(), style = LabelStyles.caps, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(names, selectedIndex = names.indexOf(current), onSelect = { onSelect(names[it]) })
    ChipRow(ranges.map { it.label }, selectedIndex = rangeIdx, onSelect = { rangeIdx = it })

    val points = remember(entries, current, rangeIdx) { ProgressChart.points(entries, current, ranges[rangeIdx], LocalDate.now()) }
    val first = points.firstOrNull()
    val last = points.lastOrNull()
    LineChartCard(
        title = stringResource(R.string.chart_legend_weight),
        points = points.map { it.maxKg.toFloat() },
        trailing = last?.let { stringResource(R.string.screens_last_value, UiFormat.number(it.maxKg)) },
        startLabel = first?.let { LocalDate.ofEpochDay(it.epochDay).format(DateFmt) },
        endLabel = if (points.size > 1) last?.let { LocalDate.ofEpochDay(it.epochDay).format(DateFmt) } else null,
        emptyText = stringResource(R.string.chart_empty),
        onePointText = stringResource(R.string.chart_one_point),
    )
    val rm = points.filter { it.oneRm != null }
    if (rm.isNotEmpty()) {
        LineChartCard(
            title = stringResource(R.string.chart_legend_1rm),
            points = rm.map { it.oneRm!!.toFloat() },
            trailing = stringResource(R.string.screens_last_value, UiFormat.number(rm.last().oneRm!!, 1)),
            startLabel = LocalDate.ofEpochDay(rm.first().epochDay).format(DateFmt),
            endLabel = if (rm.size > 1) LocalDate.ofEpochDay(rm.last().epochDay).format(DateFmt) else null,
            onePointText = stringResource(R.string.chart_one_point),
        )
    }
}

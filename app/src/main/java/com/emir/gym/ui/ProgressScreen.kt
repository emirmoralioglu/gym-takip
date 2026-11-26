package com.emir.gym.ui

import com.emir.gym.ui.theme.tabular
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.ChartPoint
import com.emir.gym.data.ChartRange
import com.emir.gym.data.ExerciseRecord
import com.emir.gym.data.ProgressChart
import com.emir.gym.data.Records
import com.emir.gym.data.WorkoutEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val shortDayFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(entries: List<WorkoutEntry>?, modifier: Modifier = Modifier) {
    if (entries == null) {
        Text("Yükleniyor...", modifier.padding(24.dp))
        return
    }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(section == 0, { section = 0 }, label = { Text(stringResource(R.string.progress_records)) })
            FilterChip(section == 1, { section = 1 }, label = { Text(stringResource(R.string.progress_chart)) })
        }
        if (section == 0) {
            RecordsSection(entries) { name -> selected = name; section = 1 }
        } else {
            ChartSection(entries, selected) { selected = it }
        }
    }
}

@Composable
private fun RecordsSection(entries: List<WorkoutEntry>, onOpen: (String) -> Unit) {
    val records = remember(entries) { Records.compute(entries) }
    if (records.isEmpty()) {
        Text(stringResource(R.string.records_empty))
        return
    }
    records.forEach { r -> RecordCard(r) { onOpen(r.exercise) } }
    Text(stringResource(R.string.records_epley_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun RecordCard(r: ExerciseRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.exercise, style = MaterialTheme.typography.titleMedium)
            Text(
                "${stringResource(R.string.records_best_weight)}: ${fmtKg(r.maxWeightKg)} kg · ${LocalDate.ofEpochDay(r.maxWeightDay).format(dayFmt)}",
                style = MaterialTheme.typography.bodyLarge.tabular(),
            )
            if (r.best1Rm != null && r.best1RmDay != null) {
                Text(
                    "${stringResource(R.string.records_best_1rm)}: ${fmtKg1(r.best1Rm)} kg · ${LocalDate.ofEpochDay(r.best1RmDay).format(dayFmt)}",
                    style = MaterialTheme.typography.bodyLarge.tabular(),
                )
            }
        }
    }
}

/** 1RM tahmini icin tek ondalik (tam sayiysa ondaliksiz). */
private fun fmtKg1(v: Double): String = fmtKg(Math.round(v * 10) / 10.0)

@Composable
private fun ChartSection(entries: List<WorkoutEntry>, selected: String?, onSelect: (String) -> Unit) {
    val names = remember(entries) { ProgressChart.exerciseNames(entries) }
    if (names.isEmpty()) {
        Text(stringResource(R.string.chart_no_exercise))
        return
    }
    val current = names.firstOrNull { it.equals(selected, ignoreCase = true) } ?: names.first()
    var range by rememberSaveable { mutableStateOf(ChartRange.WEEKS_12) }

    Text(stringResource(R.string.chart_pick_exercise), style = MaterialTheme.typography.labelLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(names) { n -> FilterChip(n == current, { onSelect(n) }, label = { Text(n) }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartRange.entries.forEach { r -> FilterChip(range == r, { range = r }, label = { Text(r.label) }) }
    }
    val points = remember(entries, current, range) { ProgressChart.points(entries, current, range, LocalDate.now()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(current, style = MaterialTheme.typography.titleMedium)
            if (points.isEmpty()) {
                Text(stringResource(R.string.chart_empty))
            } else {
                LineChart(points)
                if (points.size == 1) Text(stringResource(R.string.chart_one_point), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LineChart(points: List<ChartPoint>) {
    val kgColor = MaterialTheme.colorScheme.primary
    val rmColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rmPoints = points.filter { it.oneRm != null }
    val lo = points.minOf { minOf(it.maxKg, it.oneRm ?: it.maxKg) }
    val hi = points.maxOf { maxOf(it.maxKg, it.oneRm ?: it.maxKg) }
    // Tek deger ya da cok dar aralikta cizgi tavana yapismasin.
    val pad = maxOf((hi - lo) * 0.1, 1.0)
    val yMin = maxOf(0.0, lo - pad)
    val yMax = hi + pad
    val d0 = points.first().epochDay
    val d1 = points.last().epochDay

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(48.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(fmtKg1(yMax), style = MaterialTheme.typography.labelSmall)
            Text(fmtKg1(yMin), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            fun x(day: Long) = if (d1 == d0) w / 2 else ((day - d0).toFloat() / (d1 - d0)) * w
            fun y(v: Double) = (h - ((v - yMin) / (yMax - yMin)).toFloat() * h)
            for (i in 0..2) drawLine(grid, Offset(0f, h * i / 2), Offset(w, h * i / 2), 1f)
            fun series(color: Color, pts: List<ChartPoint>, value: (ChartPoint) -> Double) {
                val sw = 3.dp.toPx()
                pts.zipWithNext { a, b ->
                    drawLine(color, Offset(x(a.epochDay), y(value(a))), Offset(x(b.epochDay), y(value(b))), sw, StrokeCap.Round)
                }
                pts.forEach { drawCircle(color, 4.dp.toPx(), Offset(x(it.epochDay), y(value(it)))) }
            }
            // 12+ tekrarli gunlerin 1RM'i yok; cizgi bu gunleri atlar.
            series(rmColor, rmPoints) { it.oneRm!! }
            series(kgColor, points) { it.maxKg }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(LocalDate.ofEpochDay(d0).format(shortDayFmt), style = MaterialTheme.typography.labelSmall)
        if (d1 != d0) Text(LocalDate.ofEpochDay(d1).format(shortDayFmt), style = MaterialTheme.typography.labelSmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.chart_legend_weight), color = kgColor, style = MaterialTheme.typography.labelMedium)
        if (rmPoints.isNotEmpty()) Text(stringResource(R.string.chart_legend_1rm), color = rmColor, style = MaterialTheme.typography.labelMedium)
    }
}

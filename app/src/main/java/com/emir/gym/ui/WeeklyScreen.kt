package com.emir.gym.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.emir.gym.data.WeekSummary
import com.emir.gym.data.WeeklyStats
import com.emir.gym.data.WorkoutEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val shortFmt = DateTimeFormatter.ofPattern("dd.MM")
private val dayNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@Composable
fun WeeklyScreen(entries: List<WorkoutEntry>?, modifier: Modifier = Modifier) {
    if (entries == null) {
        Text("Yükleniyor...", modifier.padding(24.dp))
        return
    }
    val thisWeek = WeeklyStats.weekStart(LocalDate.now())
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeekCard("Bu hafta", WeeklyStats.summarize(entries, thisWeek))
        WeekCard("Geçen hafta", WeeklyStats.summarize(entries, thisWeek.minusWeeks(1)))
    }
}

@Composable
private fun WeekCard(title: String, s: WeekSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$title (${s.weekStart.format(shortFmt)} - ${s.weekStart.plusDays(6).format(shortFmt)})",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Toplam set: ${s.totalSets}")
            Text("Toplam hacim: ${fmtKg(s.totalVolume)} kg")
            Text("Antrenman günü: ${s.trainingDays}")
            if (s.totalSets == 0) Text("Bu hafta kayıt yok.", style = MaterialTheme.typography.bodySmall)
            BarChart(s.dailyVolume)
        }
    }
}

@Composable
private fun BarChart(values: List<Double>) {
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val slot = size.width / values.size
        val barW = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = (size.height * (v / max)).toFloat()
            val x = i * slot + (slot - barW) / 2
            if (h > 0f) drawRoundRect(barColor, Offset(x, size.height - h), Size(barW, h), CornerRadius(6f, 6f))
        }
        drawLine(baseColor, Offset(0f, size.height), Offset(size.width, size.height), 2f)
    }
    Row(Modifier.fillMaxWidth()) {
        dayNames.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

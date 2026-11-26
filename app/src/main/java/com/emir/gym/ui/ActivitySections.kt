package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.GymChip
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.NumberStyles

private const val MAX_MINUTES = 1000
private const val MAX_DISTANCE_M = 100_000

/** Yüzme günü formu: süre (dk), isteğe bağlı mesafe (m) ve not. */
@Composable
fun SwimForm(vm: GymViewModel, epochDay: Long) {
    var minutes by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minutesErr = stringResource(R.string.screens_swim_minutes_error, MAX_MINUTES)
    val distanceErr = stringResource(R.string.screens_swim_distance_error, MAX_DISTANCE_M)
    val saved = stringResource(R.string.screens_swim_saved)

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_swim_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GymField(minutes, { minutes = it.filter(Char::isDigit).take(4); error = null }, stringResource(R.string.screens_swim_minutes), keyboardType = KeyboardType.Number, suffix = stringResource(R.string.screens_unit_min))
            GymField(distance, { distance = it.filter(Char::isDigit).take(6); error = null }, stringResource(R.string.screens_swim_distance), keyboardType = KeyboardType.Number, suffix = stringResource(R.string.screens_unit_m))
            GymField(note, { note = it }, stringResource(R.string.screens_note))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                stringResource(R.string.screens_log_swim),
                onClick = {
                    val m = minutes.toIntOrNull()
                    val d = if (distance.isBlank()) null else distance.toIntOrNull()
                    when {
                        m == null || m < 1 || m > MAX_MINUTES -> error = minutesErr
                        distance.isNotBlank() && (d == null || d < 1 || d > MAX_DISTANCE_M) -> error = distanceErr
                        else -> {
                            vm.saveActivity(ActivityLog(epochDay = epochDay, type = ActivityType.YUZME, minutes = m, distanceM = d, note = note.trim()))
                            vm.notify(saved)
                            minutes = ""; distance = ""; note = ""; error = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = GymIcons.Plus,
            )
        }
    }
}

/** "Sonrası" kartı: sauna ve buhar için hızlı süre çipleri (10, 15 dk, özel). */
@Composable
fun AfterCard(vm: GymViewModel, epochDay: Long) {
    var custom by remember { mutableStateOf<ActivityType?>(null) }
    val context = LocalContext.current

    fun add(type: ActivityType, minutes: Int) {
        vm.saveActivity(ActivityLog(epochDay = epochDay, type = type, minutes = minutes))
        val label = context.getString(if (type == ActivityType.SAUNA) R.string.screens_act_sauna else R.string.screens_act_steam)
        vm.notify(context.getString(R.string.screens_after_added, label, minutes))
    }

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_after_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        listOf(ActivityType.SAUNA, ActivityType.BUHAR).forEach { t ->
            Spacer(Modifier.height(10.dp))
            Text(activityLabel(t), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15).forEach { m ->
                    GymChip(stringResource(R.string.screens_minutes, m.toString()), selected = false, onClick = { add(t, m) })
                }
                GymChip(stringResource(R.string.screens_custom), selected = false, onClick = { custom = t })
            }
        }
    }

    custom?.let { t ->
        var text by remember { mutableStateOf("") }
        var err by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { custom = null },
            title = { Text(activityLabel(t)) },
            text = {
                Column {
                    GymField(
                        text, { text = it.filter(Char::isDigit).take(4); err = false },
                        stringResource(R.string.screens_minutes_field),
                        keyboardType = KeyboardType.Number, isError = err, suffix = stringResource(R.string.screens_unit_min),
                    )
                    if (err) Text(stringResource(R.string.screens_swim_minutes_error, MAX_MINUTES), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = text.toIntOrNull()
                    if (m == null || m < 1 || m > MAX_MINUTES) err = true else { add(t, m); custom = null }
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = { TextButton(onClick = { custom = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Seçili tarihin aktivite kayıtları; silme onaylıdır. */
@Composable
fun ActivityList(vm: GymViewModel, epochDay: Long, activities: List<ActivityLog>) {
    val day = activities.filter { it.epochDay == epochDay }.sortedBy { it.id }
    if (day.isEmpty()) return
    SectionHeader(stringResource(R.string.screens_records))
    AppCard(Modifier.fillMaxWidth()) {
        day.forEach { ActivityRow(it, vm) }
    }
}

@Composable
private fun ActivityRow(log: ActivityLog, vm: GymViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(activityLabel(log.type), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val detail = listOfNotNull(
                stringResource(R.string.screens_minutes, UiFormat.grouped(log.minutes.toLong())),
                log.distanceM?.let { stringResource(R.string.screens_meters, UiFormat.grouped(it.toLong())) },
                log.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { confirm = true }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screens_delete))
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.screens_delete_record_title),
            text = stringResource(R.string.screens_delete_activity_text, activityLabel(log.type), log.minutes),
            confirmLabel = stringResource(R.string.screens_delete),
            onConfirm = { confirm = false; vm.deleteActivity(log) },
            onDismiss = { confirm = false },
        )
    }
}

/** Yüzme/dinlenme günlerinde Geçmiş: o hafta gününe düşen tarihlerin aktiviteleri. */
@Composable
fun ActivityHistory(vm: GymViewModel, weekday: Int, activities: List<ActivityLog>) {
    val history = remember(weekday, activities) { FolderLogic.activityHistory(weekday, activities) }
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_history_empty_title),
            message = stringResource(R.string.screens_history_empty_activity),
            icon = GymIcons.Today,
        )
        return
    }
    history.forEach { (date, logs) ->
        AppCard(Modifier.fillMaxWidth()) {
            Text(dateWithWeekday(date), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            logs.forEach { ActivityRow(it, vm) }
        }
    }
}

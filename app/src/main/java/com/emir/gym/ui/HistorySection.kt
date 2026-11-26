package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.EntryForm
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.FormResult
import com.emir.gym.data.PlanLogic
import com.emir.gym.data.WorkoutEntry
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.NumberStyles

/** Ağırlık günlerinde Geçmiş: o hafta gününe düşen tarihler, en yeni başta. */
@Composable
fun WorkoutHistory(vm: GymViewModel, weekday: Int, entries: List<WorkoutEntry>) {
    val history = remember(weekday, entries) { PlanLogic.weekdayHistory(weekday, entries) }
    var open by remember { mutableStateOf<Long?>(null) }
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_history_empty_title),
            message = stringResource(R.string.screens_history_empty_workout),
            icon = GymIcons.Today,
        )
        return
    }
    history.forEach { log ->
        AppCard(Modifier.fillMaxWidth(), onClick = { open = log.date.toEpochDay() }) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    dateWithWeekday(log.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.screens_tonnage_kg, UiFormat.grouped(Math.round(FolderLogic.tonnage(log.entries)))),
                    style = NumberStyles.small,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            FolderLogic.lines(log.entries).forEach {
                Text(it.display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    // Açık gün, güncel listeden okunur: düzenleme/silme sonrası içerik kendiliğinden tazelenir.
    open?.let { day ->
        val log = history.firstOrNull { it.date.toEpochDay() == day }
        if (log == null) open = null else DayEntriesDialog(vm, log.date, log.entries) { open = null }
    }
}

@Composable
private fun DayEntriesDialog(vm: GymViewModel, date: java.time.LocalDate, entries: List<WorkoutEntry>, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<WorkoutEntry?>(null) }
    var deleting by remember { mutableStateOf<WorkoutEntry?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(dateWithWeekday(date)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entries.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ExerciseCatalog.displayName(e.exercise), style = MaterialTheme.typography.titleSmall)
                            val kg = if (e.weightKg > 0.0) " @ ${UiFormat.number(e.weightKg)}" else ""
                            Text("${e.sets}×${e.reps}$kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = e }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.screens_edit))
                        }
                        IconButton(onClick = { deleting = e }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screens_delete))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } },
    )
    editing?.let { e ->
        EntryEditDialog(e, onSave = { vm.save(it); editing = null }, onDismiss = { editing = null })
    }
    deleting?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.screens_delete_record_title),
            text = stringResource(R.string.screens_delete_entry_text, ExerciseCatalog.displayName(e.exercise), e.sets, e.reps),
            confirmLabel = stringResource(R.string.screens_delete),
            onConfirm = { deleting = null; vm.delete(e) },
            onDismiss = { deleting = null },
        )
    }
}

/** Geçmiş kaydı düzenleme: set, tekrar, kg (nokta ondalık; virgül noktaya çevrilir) ve not. */
@Composable
private fun EntryEditDialog(entry: WorkoutEntry, onSave: (WorkoutEntry) -> Unit, onDismiss: () -> Unit) {
    var sets by remember { mutableStateOf(entry.sets.toString()) }
    var reps by remember { mutableStateOf(entry.reps.toString()) }
    var kg by remember { mutableStateOf(FolderLogic.kgText(entry.weightKg)) }
    var note by remember { mutableStateOf(entry.note) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ExerciseCatalog.displayName(entry.exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField(sets, { sets = UiFormat.sanitizeReps(it); error = null }, stringResource(R.string.screens_sets), Modifier.weight(1f), KeyboardType.Number)
                    GymField(reps, { reps = UiFormat.sanitizeReps(it); error = null }, stringResource(R.string.screens_reps), Modifier.weight(1f), KeyboardType.Number)
                }
                GymField(kg, { kg = UiFormat.sanitizeKg(it); error = null }, stringResource(R.string.screens_kg_field), keyboardType = KeyboardType.Decimal, suffix = stringResource(R.string.screens_kg))
                GymField(note, { note = it }, stringResource(R.string.screens_note))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = EntryForm.parse(entry.id, entry.epochDay, entry.exercise, sets, reps, kg, note, entry.muscleGroup)) {
                    is FormResult.Ok -> onSave(r.entry)
                    is FormResult.Error -> error = r.message
                }
            }) { Text(stringResource(R.string.screens_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

package com.emir.gym.ui

import com.emir.gym.ui.theme.tabular
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.emir.gym.R
import com.emir.gym.data.CustomExercise
import com.emir.gym.data.EntryForm
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.MuscleGroup
import com.emir.gym.data.FormResult
import com.emir.gym.data.WorkoutEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun fmtKg(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)

@Composable
fun DailyScreen(
    entries: List<WorkoutEntry>?,
    customExercises: List<CustomExercise>,
    restEndAt: Long?,
    onSave: (WorkoutEntry) -> Unit,
    onDelete: (WorkoutEntry) -> Unit,
    onCopy: (List<WorkoutEntry>) -> Unit,
    onAddCustom: (CustomExercise) -> Unit,
    onStartRest: (Int) -> Unit,
    onCancelRest: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // null: yeni kayit formu kapali; sentinel ile yeni/duzenle ayrilir.
    var editing by remember { mutableStateOf<WorkoutEntry?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<WorkoutEntry?>(null) }
    var copying by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Yeni kayıt")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (entries == null) {
                Text("Yükleniyor...", Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "rest") { RestTimerCard(restEndAt, onStartRest, onCancelRest) }
                    item(key = "copy") {
                        OutlinedButton(onClick = { copying = true }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.copy_button))
                        }
                    }
                    if (entries.isEmpty()) {
                        item(key = "empty") {
                            Text("Henüz kayıt yok. Sağ alttaki + ile ekle.", Modifier.padding(vertical = 24.dp))
                        }
                    }
                    items(entries, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth().clickable { editing = e; showForm = true }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.exercise, style = MaterialTheme.typography.titleMedium)
                                    val group = ExerciseCatalog.groupOf(e, customExercises)
                                    val date = LocalDate.ofEpochDay(e.epochDay).format(dateFmt)
                                    Text(
                                        if (group != null) "$date · ${group.label}" else date,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text("${e.sets} set × ${e.reps} tekrar × ${fmtKg(e.weightKg)} kg", style = MaterialTheme.typography.bodyLarge.tabular())
                                    if (e.note.isNotEmpty()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleting = e }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Sil")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (copying && entries != null) {
        CopyWorkoutDialog(entries, onCopy, onNotice, onDismiss = { copying = false })
    }
    if (showForm) {
        EntryDialog(
            initial = editing,
            custom = customExercises,
            onAddCustom = onAddCustom,
            onDismiss = { showForm = false },
            onSubmit = { onSave(it); showForm = false },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Kaydı sil?") },
            text = { Text("${e.exercise} (${LocalDate.ofEpochDay(e.epochDay).format(dateFmt)}) silinecek.") },
            confirmButton = { TextButton(onClick = { onDelete(e); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDialog(
    initial: WorkoutEntry?,
    custom: List<CustomExercise>,
    onAddCustom: (CustomExercise) -> Unit,
    onDismiss: () -> Unit, onSubmit: (WorkoutEntry) -> Unit,
) {
    var epochDay by remember { mutableStateOf(initial?.epochDay ?: LocalDate.now().toEpochDay()) }
    var exercise by remember { mutableStateOf(initial?.exercise ?: "") }
    var sets by remember { mutableStateOf(initial?.sets?.toString() ?: "") }
    var reps by remember { mutableStateOf(initial?.reps?.toString() ?: "") }
    var weight by remember { mutableStateOf(initial?.let { fmtKg(it.weightKg) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var group by remember { mutableStateOf(initial?.muscleGroup ?: "") }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni kayıt" else "Kaydı düzenle") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, Modifier.fillMaxWidth()) {
                    Text("Tarih: " + LocalDate.ofEpochDay(epochDay).format(dateFmt))
                }
                OutlinedButton(onClick = { picking = true }, Modifier.fillMaxWidth()) {
                    val g = MuscleGroup.labelOf(group)
                    Text(
                        if (exercise.isBlank()) stringResource(R.string.exercise_pick)
                        else if (g.isNotEmpty()) "$exercise · $g" else exercise,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sets, { sets = it }, label = { Text("Set") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(reps, { reps = it }, label = { Text("Tekrar") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(weight, { weight = it }, label = { Text("Ağırlık (kg)") }, singleLine = true, keyboardOptions = dec, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Not") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = EntryForm.parse(initial?.id ?: 0, epochDay, exercise, sets, reps, weight, note, group)) {
                    is FormResult.Ok -> onSubmit(r.entry)
                    is FormResult.Error -> error = r.message
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )

    if (picking) {
        ExercisePickerDialog(
            custom = custom,
            onPick = { name, g -> exercise = name; group = g?.name ?: ""; error = null; picking = false },
            onAddCustom = onAddCustom,
            onDismiss = { picking = false },
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Vazgeç") } },
        ) { DatePicker(state) }
    }
}

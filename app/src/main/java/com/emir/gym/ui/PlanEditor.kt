package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.PlanExercise
import com.emir.gym.data.PlanLogic
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.PrimaryButton

private const val MAX_PLAN_SETS = 20
private const val MAX_PLAN_REPS = 100
private const val MIN_REST = 10
private const val MAX_REST = 3600

/** Planı düzenle: sırala, hareket bilgilerini değiştir, sil (onaylı). */
@Composable
fun PlanEditor(vm: GymViewModel, weekday: Int, exercises: List<PlanExercise>, onDone: () -> Unit) {
    var editing by remember { mutableStateOf<PlanExercise?>(null) }
    var deleting by remember { mutableStateOf<PlanExercise?>(null) }

    Text(stringResource(R.string.screens_edit_plan), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    if (exercises.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_plan_empty_title),
            message = stringResource(R.string.screens_plan_empty_message),
        )
    }
    exercises.forEachIndexed { i, pe ->
        AppCard(Modifier.fillMaxWidth()) {
            Text(ExerciseCatalog.displayName(pe.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.screens_plan_summary, pe.sets, pe.targetReps, pe.restSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pe.note.isNotBlank()) {
                Text(pe.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = i > 0, onClick = { vm.reorderPlan(weekday, PlanLogic.move(exercises, i, i - 1).map { it.id }) }) {
                    Icon(GymIcons.ArrowUp, contentDescription = stringResource(R.string.screens_move_up))
                }
                IconButton(enabled = i < exercises.lastIndex, onClick = { vm.reorderPlan(weekday, PlanLogic.move(exercises, i, i + 1).map { it.id }) }) {
                    Icon(GymIcons.ArrowDown, contentDescription = stringResource(R.string.screens_move_down))
                }
                IconButton(onClick = { editing = pe }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.screens_edit))
                }
                IconButton(onClick = { deleting = pe }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screens_delete))
                }
            }
        }
    }
    PrimaryButton(stringResource(R.string.screens_done), onClick = onDone, modifier = Modifier.fillMaxWidth())

    editing?.let { pe -> PlanExerciseDialog(pe, onSave = { vm.updatePlanExercise(it); editing = null }, onDismiss = { editing = null }) }
    deleting?.let { pe ->
        ConfirmDialog(
            title = stringResource(R.string.screens_delete_plan_exercise_title),
            text = stringResource(R.string.screens_delete_plan_exercise_text, ExerciseCatalog.displayName(pe.name)),
            confirmLabel = stringResource(R.string.screens_delete),
            onConfirm = { deleting = null; vm.removePlanExercise(pe) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun PlanExerciseDialog(pe: PlanExercise, onSave: (PlanExercise) -> Unit, onDismiss: () -> Unit) {
    var sets by remember { mutableStateOf(pe.sets.toString()) }
    var reps by remember { mutableStateOf(pe.targetReps.toString()) }
    var rest by remember { mutableStateOf(pe.restSeconds.toString()) }
    var note by remember { mutableStateOf(pe.note) }
    var error by remember { mutableStateOf<String?>(null) }
    val err = stringResource(R.string.screens_plan_edit_error, MAX_PLAN_SETS, MAX_PLAN_REPS, MIN_REST, MAX_REST)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ExerciseCatalog.displayName(pe.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField(sets, { sets = it.filter(Char::isDigit).take(2); error = null }, stringResource(R.string.screens_sets), Modifier.weight(1f), KeyboardType.Number)
                    GymField(reps, { reps = it.filter(Char::isDigit).take(3); error = null }, stringResource(R.string.screens_reps), Modifier.weight(1f), KeyboardType.Number)
                }
                GymField(rest, { rest = it.filter(Char::isDigit).take(4); error = null }, stringResource(R.string.screens_rest_seconds), keyboardType = KeyboardType.Number, suffix = stringResource(R.string.screens_unit_sec))
                GymField(note, { note = it }, stringResource(R.string.screens_note))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = sets.toIntOrNull()
                val r = reps.toIntOrNull()
                val t = rest.toIntOrNull()
                if (s == null || s !in 1..MAX_PLAN_SETS || r == null || r !in 1..MAX_PLAN_REPS || t == null || t !in MIN_REST..MAX_REST) {
                    error = err
                } else {
                    onSave(pe.copy(sets = s, targetReps = r, restSeconds = t, note = note.trim()))
                }
            }) { Text(stringResource(R.string.screens_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

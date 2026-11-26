package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.CopyPlan
import com.emir.gym.data.WorkoutCopy
import com.emir.gym.data.WorkoutEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val copyDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Gecmis bir gunun (ya da gecen haftanin ayni gununun) kayitlarini bugune kopyalar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyWorkoutDialog(
    entries: List<WorkoutEntry>,
    onCopy: (List<WorkoutEntry>) -> Unit,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var picking by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<CopyPlan.Ready?>(null) }

    val emptyMsg = stringResource(R.string.copy_empty_source, "%s")
    val sameMsg = stringResource(R.string.copy_same_day)

    fun start(source: LocalDate) {
        when (val p = WorkoutCopy.plan(entries, source, today)) {
            is CopyPlan.EmptySource -> { onNotice(emptyMsg.format(p.source.format(copyDateFmt))); onDismiss() }
            CopyPlan.SameDay -> { onNotice(sameMsg); onDismiss() }
            is CopyPlan.Ready -> if (p.needsConfirm) pending = p else { onCopy(p.items); onDismiss() }
        }
    }

    val lastWeek = WorkoutCopy.lastWeekSameDay(today)
    val selectable = remember(entries) { WorkoutCopy.daysWithEntriesBefore(entries, today) }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.copy_confirm_title)) },
            text = { Text(stringResource(R.string.copy_confirm_text, p.duplicates, p.items.size)) },
            confirmButton = {
                TextButton(onClick = { onCopy(p.items); pending = null; onDismiss() }) {
                    Text(stringResource(R.string.copy_confirm_yes))
                }
            },
            dismissButton = { TextButton(onClick = { pending = null; onDismiss() }) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }

    if (picking) {
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Math.floorDiv(utcTimeMillis, 86_400_000L) in selectable
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { start(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                        picking = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state, title = { Text(stringResource(R.string.copy_pick_title), Modifier.padding(start = 24.dp, top = 16.dp)) }) }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.copy_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.copy_target_info))
                OutlinedButton(onClick = { start(lastWeek) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.copy_last_week, lastWeek.format(copyDateFmt)))
                }
                OutlinedButton(
                    onClick = { picking = true },
                    enabled = selectable.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.copy_pick_day)) }
                if (selectable.isEmpty()) Text(stringResource(R.string.copy_none_selectable))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

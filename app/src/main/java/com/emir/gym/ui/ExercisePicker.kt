package com.emir.gym.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.CustomExercise
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.MuscleGroup
import com.emir.gym.ui.theme.Radii

private sealed interface PickerRow {
    data class Header(val title: String) : PickerRow
    data class Item(val name: String, val group: MuscleGroup?) : PickerRow
    data object AddCustom : PickerRow
}

/** Kas grubuna gore gruplanmis hazir liste + ozel egzersizler + "kendi egzersizini ekle". */
@Composable
fun ExercisePickerDialog(
    custom: List<CustomExercise>,
    onPick: (name: String, group: MuscleGroup?) -> Unit,
    onAddCustom: (CustomExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val customHeader = stringResource(R.string.exercise_custom_section)
    val rows = remember(custom, customHeader, query) {
        val q = ExerciseCatalog.key(query)
        fun matches(name: String) = q.isEmpty() || ExerciseCatalog.key(name).contains(q)
        buildList {
            add(PickerRow.AddCustom)
            val mine = custom.filter { matches(it.name) }
            if (mine.isNotEmpty()) {
                add(PickerRow.Header(customHeader))
                mine.forEach { add(PickerRow.Item(it.name, MuscleGroup.fromKey(it.muscleGroup))) }
            }
            ExerciseCatalog.byGroup().forEach { (g, list) ->
                val found = list.filter { matches(it.name) }
                if (found.isNotEmpty()) {
                    add(PickerRow.Header(g.label))
                    found.forEach { add(PickerRow.Item(it.name, g)) }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_picker_title)) },
        text = {
            Column {
            OutlinedTextField(
                query, { query = it },
                label = { Text(stringResource(R.string.screens_search)) },
                singleLine = true,
                shape = Radii.inner,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(rows) { row ->
                    when (row) {
                        is PickerRow.Header -> Text(
                            row.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        )
                        is PickerRow.Item -> Text(
                            row.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(row.name, row.group) }.padding(vertical = 12.dp),
                        )
                        PickerRow.AddCustom -> Column {
                            Text(
                                stringResource(R.string.exercise_add_custom),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth().clickable { adding = true }.padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (adding) {
        AddCustomExerciseDialog(
            custom = custom,
            onAdd = { ex ->
                onAddCustom(ex)
                adding = false
                onPick(ex.name, MuscleGroup.fromKey(ex.muscleGroup))
            },
            onDismiss = { adding = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomExerciseDialog(custom: List<CustomExercise>, onAdd: (CustomExercise) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it; error = null },
                    label = { Text(stringResource(R.string.exercise_custom_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.exercise_custom_group), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleGroup.entries.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = g; error = null }, label = { Text(g.label) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = ExerciseCatalog.validateCustom(name, group, custom)
                if (err != null) error = err else onAdd(CustomExercise(name.trim(), group!!.name))
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

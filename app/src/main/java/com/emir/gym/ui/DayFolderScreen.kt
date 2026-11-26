package com.emir.gym.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.MuscleGroup
import com.emir.gym.data.PlanDayType
import com.emir.gym.data.PlanExercise
import com.emir.gym.data.PlanLogic
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.ChipRow
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.ExerciseCard
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.SecondaryButton
import com.emir.gym.ui.components.SetRow
import com.emir.gym.ui.components.SetRowHeader
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate

/** Bir hafta gününün klasörü: Plan ve Geçmiş bölümleri. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayFolderScreen(vm: GymViewModel, weekday: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    var epochDay by rememberSaveable(weekday) { mutableLongStateOf(FolderLogic.nearestDate(weekday, today).toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    val entries by vm.entries.collectAsState()
    val days by vm.planDays.collectAsState()
    val allExercises by vm.planExercises.collectAsState()
    val activities by vm.activities.collectAsState()
    val rows by vm.setRows.collectAsState()
    val custom by vm.customExercises.collectAsState()

    var section by rememberSaveable(weekday) { mutableIntStateOf(0) }
    var editing by rememberSaveable(weekday) { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

    val plan = PlanLogic.planOf(weekday, days, allExercises)
    val type = plan?.day?.type ?: PlanDayType.DINLENME
    val list = entries

    BackHandler { if (editing) editing = false else onBack() }

    // Satirlari o tarihin kayitlarindan kur; kayitlar ya da plan degisince tazele.
    LaunchedEffect(list, epochDay, plan?.exercises) {
        if (list != null && plan != null) vm.syncRows(date, plan.exercises)
    }

    ScreenColumn(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (editing) editing = false else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.screens_back))
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(weekdayName(weekday), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                val minutes = FolderLogic.estimateMinutes(plan?.day, plan?.exercises.orEmpty())
                val sub = listOfNotNull(
                    plan?.day?.title?.takeIf { it.isNotBlank() },
                    minutes?.let { stringResource(R.string.screens_approx_minutes, it) },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (list == null) {
            Text(stringResource(R.string.screens_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScreenColumn
        }

        if (editing) {
            PlanEditor(vm, weekday, plan?.exercises.orEmpty(), onDone = { editing = false })
            return@ScreenColumn
        }

        DateCard(date, today) { pickDate = true }

        ChipRow(
            listOf(stringResource(R.string.screens_section_plan), stringResource(R.string.screens_section_history)),
            selectedIndex = section,
            onSelect = { section = it },
        )

        if (section == 1) {
            if (type == PlanDayType.AGIRLIK) WorkoutHistory(vm, weekday, list)
            else ActivityHistory(vm, weekday, activities)
            return@ScreenColumn
        }

        when (type) {
            PlanDayType.AGIRLIK -> WeightPlan(vm, weekday, date, plan?.exercises.orEmpty(), list, rows, onEdit = { editing = true })
            PlanDayType.YUZME -> SwimForm(vm, epochDay)
            PlanDayType.DINLENME -> AppCard(Modifier.fillMaxWidth()) {
                EmptyState(
                    title = plan?.day?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.screens_rest_day),
                    message = plan?.day?.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.screens_rest_message),
                    icon = GymIcons.Today,
                    modifier = Modifier.padding(0.dp),
                )
            }
        }
        AfterCard(vm, epochDay)
        ActivityList(vm, epochDay, activities)
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    FolderLogic.isSelectable(LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L)), weekday, today)

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { epochDay = Math.floorDiv(it, 86_400_000L) }
                        pickDate = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DateCard(date: LocalDate, today: LocalDate, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.screens_date_label).upperTr(),
                    style = LabelStyles.caps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    date.format(DateFmt) + if (date == today) " · " + stringResource(R.string.screens_today) else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(stringResource(R.string.screens_change), style = MaterialTheme.typography.labelLarge, color = GymTheme.colors.accent)
        }
    }
}

@Composable
private fun WeightPlan(
    vm: GymViewModel,
    weekday: Int,
    date: LocalDate,
    exercises: List<PlanExercise>,
    entries: List<com.emir.gym.data.WorkoutEntry>,
    rows: Map<RowKey, RowState>,
    onEdit: () -> Unit,
) {
    val epochDay = date.toEpochDay()
    val custom by vm.customExercises.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var confirmFill by remember { mutableStateOf<LocalDate?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val noSource = stringResource(R.string.screens_fill_none)

    fun doFill(source: LocalDate) {
        val n = vm.fillRows(exercises, source)
        vm.notify(
            if (n == 0) noSource
            else context.getString(R.string.screens_fill_done, n, source.format(DateFmt)),
        )
    }

    if (exercises.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.screens_plan_empty_title),
            message = stringResource(R.string.screens_plan_empty_message),
            actionLabel = stringResource(R.string.screens_add_exercise),
            onAction = { picking = true },
        )
    } else {
        SecondaryButton(
            stringResource(R.string.screens_fill_last),
            onClick = {
                val src = FolderLogic.lastSourceDate(weekday, date, entries)
                when {
                    src == null -> vm.notify(noSource)
                    FolderLogic.fillWouldOverwrite(rows.filterKeys { it.epochDay == epochDay }) -> confirmFill = src
                    else -> doFill(src)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        exercises.forEach { pe ->
            val sets = rows.filterKeys { it.planId == pe.id && it.epochDay == epochDay }.toSortedMap(compareBy<RowKey> { it.index })
            val progress = PlanLogic.exerciseProgress(pe, date, entries)
            val hint = FolderLogic.lastKg(pe.name, entries, epochDay)?.let(FolderLogic::kgText)
            val bulk = FolderLogic.lines(
                entries.filter { it.epochDay == epochDay && it.sets > 1 && ExerciseCatalog.key(it.exercise) == ExerciseCatalog.key(pe.name) },
            )
            ExerciseCard(
                name = ExerciseCatalog.displayName(pe.name),
                muscleGroup = MuscleGroup.labelOf(pe.muscleGroup).ifEmpty { null },
                plannedSets = pe.sets,
                plannedReps = pe.targetReps,
                doneSets = progress.doneSets,
            ) {
                if (pe.note.isNotBlank()) {
                    Text(pe.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                if (bulk.isNotEmpty()) {
                    Text(
                        stringResource(R.string.screens_bulk_entry, bulk.joinToString("; ") { it.text }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (sets.isNotEmpty()) SetRowHeader()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sets.forEach { (key, st) ->
                        SetRow(
                            setNumber = key.index + 1,
                            kg = st.kg,
                            onKgChange = { vm.editRow(key, pe, it, st.reps) },
                            reps = st.reps,
                            onRepsChange = { vm.editRow(key, pe, st.kg, it) },
                            done = st.done,
                            onDoneChange = { vm.toggleSet(key, pe, it) },
                            kgHint = hint,
                        )
                    }
                }
                TextButton(onClick = { vm.addExtraSet(pe, epochDay) }) {
                    Text(stringResource(R.string.screens_add_set))
                }
            }
        }
        SecondaryButton(stringResource(R.string.screens_add_exercise), onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), icon = GymIcons.Plus)
        SecondaryButton(stringResource(R.string.screens_edit_plan), onClick = onEdit, modifier = Modifier.fillMaxWidth())
    }

    if (picking) {
        val dupMsg = stringResource(R.string.screens_exercise_in_plan)
        ExercisePickerDialog(
            custom = custom,
            onPick = { name, group ->
                picking = false
                if (exercises.any { ExerciseCatalog.key(it.name) == ExerciseCatalog.key(name) }) vm.notify(dupMsg)
                else vm.addPlanExercise(PlanExercise(weekday = weekday, position = 0, name = name, muscleGroup = group?.name ?: ""))
            },
            onAddCustom = vm::addCustomExercise,
            onDismiss = { picking = false },
        )
    }
    confirmFill?.let { src ->
        ConfirmDialog(
            title = stringResource(R.string.screens_fill_confirm_title),
            text = stringResource(R.string.screens_fill_confirm_text, src.format(DateFmt)),
            confirmLabel = stringResource(R.string.screens_fill_confirm_yes),
            onConfirm = { confirmFill = null; doFill(src) },
            onDismiss = { confirmFill = null },
            destructive = false,
        )
    }
}

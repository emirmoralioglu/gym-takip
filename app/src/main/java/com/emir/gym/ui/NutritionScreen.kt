package com.emir.gym.ui

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.R
import com.emir.gym.data.Meal
import com.emir.gym.data.MealType
import com.emir.gym.data.NutritionLogic
import com.emir.gym.data.WaterLog
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.ChipRow
import com.emir.gym.ui.components.EmptyState
import com.emir.gym.ui.components.GymChip
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.upperTr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/** Beslenme sekmesi: gun secici, ozet, ogunler, su ve haftalik protein. */
@Composable
fun NutritionScreen(vm: GymViewModel, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    var epochDay by rememberSaveable { mutableLongStateOf(todayDay) }
    val date = LocalDate.ofEpochDay(epochDay)

    val meals by vm.meals.collectAsState()
    val water by vm.waterLogs.collectAsState()
    val activities by vm.activities.collectAsState()
    val weights by vm.bodyWeights.collectAsState()
    val goals by vm.goals.collectAsState()
    val ready by vm.nutritionReady.collectAsState()

    var pickDate by remember { mutableStateOf(false) }
    var formMeal by remember { mutableStateOf<Meal?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteMeal by remember { mutableStateOf<Meal?>(null) }
    var deleteWater by remember { mutableStateOf<WaterLog?>(null) }
    var customWater by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val gate = remember { ClickGate() }
    fun pass() = gate.tryPass(SystemClock.elapsedRealtime())

    // Hedef o gunden once/o gune esit en yakin tartiyla hesaplanir; tarti yoksa ayardaki kilo.
    val daily = remember(date, meals, water, goals, weights) { NutritionLogic.daily(date, meals, water, goals, weights) }
    val weightKg = remember(weights, goals, epochDay) { NutritionLogic.effectiveWeightKg(weights, goals.bodyWeightKg, epochDay) }
    val waterGoal = remember(goals.waterMl, epochDay, activities) { NutritionUi.waterGoal(goals.waterMl, epochDay, activities) }
    val dayMeals = remember(meals, epochDay) { NutritionUi.mealsOfDay(meals, epochDay) }
    val dayWater = remember(water, epochDay) { water.filter { it.epochDay == epochDay }.sortedByDescending { it.id } }
    val calTotal = remember(dayMeals) { NutritionUi.calorieTotal(dayMeals) }
    val recent = remember(meals) { NutritionUi.recentMeals(meals) }
    val week = remember(meals, epochDay) { NutritionUi.weeklyProtein(meals, epochDay) }

    ScreenColumn(modifier) {
        Text(stringResource(R.string.nutr_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)

        DayNavigator(
            date, today,
            onPrev = { epochDay = NutritionUi.shiftDay(epochDay, -1, todayDay) },
            onNext = { epochDay = NutritionUi.shiftDay(epochDay, 1, todayDay) },
            onPick = { pickDate = true },
        )

        SummaryCard(
            proteinG = daily.proteinG, calorieTotal = calTotal, waterMl = daily.waterMl,
            proteinRange = daily.proteinRange, weightKg = weightKg,
            calorieGoal = goals.calories, waterGoal = waterGoal, onOpenSettings = onOpenSettings,
        )

        SectionHeader(
            stringResource(R.string.nutr_meals),
            actionLabel = stringResource(R.string.nutr_add_meal),
            onAction = { formMeal = null; showForm = true },
        )
        if (recent.isNotEmpty()) RecentChips(recent) { m ->
            if (!pass()) return@RecentChips
            vm.saveMealThen(NutritionUi.copyTo(m, todayDay)) {
                vm.notify(context.getString(R.string.nutr_recent_added, m.description))
            }
        }
        MealsCard(ready, dayMeals, onAdd = { formMeal = null; showForm = true }, onEdit = { formMeal = it; showForm = true }, onDelete = { deleteMeal = it })

        SectionHeader(stringResource(R.string.nutr_water_section))
        WaterCard(
            ready, dayWater,
            onAdd = { ml -> if (pass()) vm.saveWater(WaterLog(epochDay = epochDay, ml = ml)) },
            onCustom = { customWater = true },
            onDelete = { deleteWater = it },
        )

        SectionHeader(stringResource(R.string.nutr_week_section))
        if (!ready) Unit
        else if (week.hasData) WeekProteinCard(week, daily.proteinRange?.start)
        else AppCard(Modifier.fillMaxWidth()) {
            EmptyState(
                title = stringResource(R.string.nutr_week_empty_title),
                message = stringResource(R.string.nutr_week_empty_message),
                icon = GymIcons.Meal,
                modifier = Modifier.padding(0.dp),
            )
        }
    }

    if (showForm) {
        MealFormSheet(
            vm = vm,
            meal = formMeal,
            epochDay = epochDay,
            onDismiss = { showForm = false },
        )
    }
    if (pickDate) DatePickerTr(epochDay, todayDay, onPick = { epochDay = it; pickDate = false }, onDismiss = { pickDate = false })
    deleteMeal?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.nutr_meal_delete_title),
            text = stringResource(R.string.nutr_meal_delete_text, m.description.ifBlank { m.mealType.label }),
            confirmLabel = stringResource(R.string.nutr_delete),
            onConfirm = { deleteMeal = null; vm.deleteMeal(m) },
            onDismiss = { deleteMeal = null },
        )
    }
    deleteWater?.let { w ->
        ConfirmDialog(
            title = stringResource(R.string.nutr_water_delete_title),
            text = stringResource(R.string.nutr_water_delete_text, UiFormat.grouped(w.ml.toLong())),
            confirmLabel = stringResource(R.string.nutr_delete),
            onConfirm = { deleteWater = null; vm.deleteWater(w) },
            onDismiss = { deleteWater = null },
        )
    }
    if (customWater) {
        CustomWaterDialog(
            onAdd = { ml -> customWater = false; vm.saveWater(WaterLog(epochDay = epochDay, ml = ml)) },
            onDismiss = { customWater = false },
        )
    }
}

@Composable
private fun RecentChips(recent: List<Meal>, onPick: (Meal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.nutr_recent).upperTr() + " · " + stringResource(R.string.nutr_recent_hint),
            style = LabelStyles.caps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { m ->
                val name = if (m.description.length > 18) m.description.take(17).trimEnd() + "…" else m.description
                GymChip(
                    label = stringResource(R.string.nutr_recent_chip, name, NutritionUi.grams(m.proteinG)),
                    selected = false,
                    onClick = { onPick(m) },
                )
            }
        }
    }
}

@Composable
private fun MealsCard(ready: Boolean, meals: List<Meal>, onAdd: () -> Unit, onEdit: (Meal) -> Unit, onDelete: (Meal) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Ilk veri gelene kadar bos durum gosterilmez (yanlis "ogun yok" parlamasi olmasin).
    if (!ready) return
    AppCard(Modifier.fillMaxWidth()) {
        if (meals.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.nutr_meals_empty_title),
                message = stringResource(R.string.nutr_meals_empty_message),
                icon = GymIcons.Meal,
                actionLabel = stringResource(R.string.nutr_add_meal),
                onAction = onAdd,
                modifier = Modifier.padding(0.dp),
            )
            return@AppCard
        }
        NutritionUi.groupByType(meals).forEachIndexed { i, (type, list) ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type.label.upperTr(), style = LabelStyles.caps, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.nutr_group_value, NutritionUi.grams(list.sumOf { it.proteinG })),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            list.forEach { MealItem(it, onEdit = { onEdit(it) }, onDelete = { onDelete(it) }) }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = com.emir.gym.ui.theme.GymTheme.colors.cardBorder)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nutr_day_total), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
            val cal = NutritionUi.calorieTotal(meals)
            val protein = NutritionUi.grams(meals.sumOf { it.proteinG })
            Text(
                if (cal.hasAny) stringResource(R.string.nutr_total_value, protein, UiFormat.grouped(cal.kcal.toLong()))
                else stringResource(R.string.nutr_total_value_nocal, protein),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
        }
        if (NutritionUi.calorieTotal(meals).hasMissing) {
            Text(stringResource(R.string.nutr_cal_missing_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaterCard(ready: Boolean, logs: List<WaterLog>, onAdd: (Int) -> Unit, onCustom: () -> Unit, onDelete: (WaterLog) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(250, 500, 1000).forEach { ml ->
                GymChip(stringResource(R.string.nutr_water_chip, UiFormat.grouped(ml.toLong())), selected = false, onClick = { onAdd(ml) })
            }
            GymChip(stringResource(R.string.nutr_water_custom), selected = false, onClick = onCustom)
        }
        Spacer(Modifier.height(8.dp))
        if (!ready) {
            // veri bekleniyor
        } else if (logs.isEmpty()) {
            Text(stringResource(R.string.nutr_water_none), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            logs.forEach { w ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nutr_water_value, UiFormat.grouped(w.ml.toLong())),
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    DeleteButton(stringResource(R.string.nutr_water_delete_title), onClick = { onDelete(w) })
                }
            }
        }
    }
}

@Composable
private fun CustomWaterDialog(onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutr_water_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GymField(
                    text, { text = NutritionUi.sanitizeInt(it, 4); error = false },
                    stringResource(R.string.nutr_water_amount), keyboardType = KeyboardType.Number,
                    suffix = stringResource(R.string.nutr_unit_ml), isError = error,
                )
                if (error) Text(stringResource(R.string.nutr_water_err, NutritionUi.MAX_WATER_ML), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ml = NutritionUi.parseWaterMl(text)
                if (ml == null) error = true else onAdd(ml)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealFormSheet(vm: GymViewModel, meal: Meal?, epochDay: Long, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember(meal) {
        mutableStateOf(
            meal?.let(MealForm::inputOf)
                ?: MealForm.Input(NutritionUi.defaultMealType(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))),
        )
    }
    var errors by remember { mutableStateOf<Map<MealForm.Field, MealForm.Error>>(emptyMap()) }
    val saveGate = remember { ClickGate(800L) }
    val errNum = stringResource(R.string.nutr_err_number, MealForm.MAX_PROTEIN.toInt().toString())
    val errCarbs = stringResource(R.string.nutr_err_number, MealForm.MAX_CARBS.toInt().toString())
    val errFat = stringResource(R.string.nutr_err_number, MealForm.MAX_FAT.toInt().toString())
    val errKcal = stringResource(R.string.nutr_err_int, MealForm.MAX_KCAL.toString())
    val errEmptyDesc = stringResource(R.string.nutr_err_desc_empty)
    val errLong = stringResource(R.string.nutr_err_desc_long, MealForm.MAX_DESC)
    val errEmptyProtein = stringResource(R.string.nutr_err_protein_empty)
    val types = MealType.entries

    fun clear(f: MealForm.Field) { errors = errors - f }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(if (meal == null) R.string.nutr_form_new else R.string.nutr_form_edit),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ChipRow(types.map { it.label }, types.indexOf(input.type), { input = input.copy(type = types[it]) })
            FormField(input.description, { input = input.copy(description = it.take(MealForm.MAX_DESC + 20)); clear(MealForm.Field.DESCRIPTION) },
                stringResource(R.string.nutr_f_desc), errors[MealForm.Field.DESCRIPTION], KeyboardType.Text, null, errorText(errors[MealForm.Field.DESCRIPTION], errEmptyDesc, errLong))
            FormField(input.protein, { input = input.copy(protein = UiFormat.sanitizeKg(it)); clear(MealForm.Field.PROTEIN) },
                stringResource(R.string.nutr_f_protein), errors[MealForm.Field.PROTEIN], KeyboardType.Decimal, stringResource(R.string.nutr_unit_g), errorText(errors[MealForm.Field.PROTEIN], errEmptyProtein, errNum))
            FormField(input.calories, { input = input.copy(calories = NutritionUi.sanitizeInt(it, 5)); clear(MealForm.Field.CALORIES) },
                stringResource(R.string.nutr_f_kcal), errors[MealForm.Field.CALORIES], KeyboardType.Number, stringResource(R.string.nutr_unit_kcal), errorText(errors[MealForm.Field.CALORIES], errKcal, errKcal))
            FormField(input.carbs, { input = input.copy(carbs = UiFormat.sanitizeKg(it)); clear(MealForm.Field.CARBS) },
                stringResource(R.string.nutr_f_carbs), errors[MealForm.Field.CARBS], KeyboardType.Decimal, stringResource(R.string.nutr_unit_g), errorText(errors[MealForm.Field.CARBS], errCarbs, errCarbs))
            FormField(input.fat, { input = input.copy(fat = UiFormat.sanitizeKg(it)); clear(MealForm.Field.FAT) },
                stringResource(R.string.nutr_f_fat), errors[MealForm.Field.FAT], KeyboardType.Decimal, stringResource(R.string.nutr_unit_g), errorText(errors[MealForm.Field.FAT], errFat, errFat))
            PrimaryButton(
                stringResource(R.string.nutr_save),
                onClick = {
                    if (!saveGate.tryPass(SystemClock.elapsedRealtime())) return@PrimaryButton
                    when (val r = MealForm.validate(input, meal?.epochDay ?: epochDay, meal?.id ?: 0)) {
                        is MealForm.Result.Invalid -> errors = r.errors
                        is MealForm.Result.Ok -> {
                            // Yazma hata verirse form acik kalir ve tekrar denenebilir.
                            vm.saveMealThen(r.meal) { onDismiss() }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun errorText(error: MealForm.Error?, emptyMsg: String, otherMsg: String): String? = when (error) {
    null -> null
    MealForm.Error.EMPTY -> emptyMsg
    else -> otherMsg
}

@Composable
private fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: MealForm.Error?,
    keyboard: KeyboardType,
    suffix: String?,
    message: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GymField(value, onChange, label, keyboardType = keyboard, suffix = suffix, isError = error != null)
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Turkce gorunumlu tarih secici; bugunden sonrasi secilemez. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerTr(epochDay: Long, todayDay: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cfg = remember(context) { Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("tr-TR")) } }
    val trContext = remember(context) { context.createConfigurationContext(cfg) }
    CompositionLocalProvider(LocalContext provides trContext, LocalConfiguration provides cfg) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    NutritionUi.utcMillisToEpochDay(utcTimeMillis) <= todayDay

                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.ofEpochDay(todayDay).year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = { state.selectedDateMillis?.let { onPick(NutritionUi.clampToToday(NutritionUi.utcMillisToEpochDay(it), todayDay)) } },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            val shown = state.selectedDateMillis?.let {
                LocalDate.ofEpochDay(NutritionUi.utcMillisToEpochDay(it)).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR")))
            }.orEmpty()
            DatePicker(
                state,
                title = { Text(stringResource(R.string.nutr_pick_date), Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), style = MaterialTheme.typography.labelLarge) },
                headline = { Text(shown, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineMedium) },
                showModeToggle = false,
            )
        }
    }
}

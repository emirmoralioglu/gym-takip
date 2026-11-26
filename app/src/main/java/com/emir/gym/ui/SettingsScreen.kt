package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.emir.gym.data.BodyWeight
import com.emir.gym.data.NutritionLogic
import com.emir.gym.ui.components.AppCard
import com.emir.gym.ui.components.LineChartCard
import com.emir.gym.ui.components.PrimaryButton
import com.emir.gym.ui.components.SecondaryButton
import com.emir.gym.ui.components.SectionHeader
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.NumberStyles
import java.time.LocalDate

/** Ayarlar: tartı, kilo ayarı, protein aralığı, su ve kalori hedefi. */
@Composable
fun SettingsScreen(vm: GymViewModel, modifier: Modifier = Modifier) {
    val goals by vm.goals.collectAsState()
    val weights by vm.bodyWeights.collectAsState()
    val today = LocalDate.now()
    val series = remember(weights) { NutritionLogic.weightSeries(weights) }

    ScreenColumn(modifier) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)

        SectionHeader(stringResource(R.string.screens_weigh_section))
        WeighInCard(vm, series, today)
        if (series.isNotEmpty()) {
            val first = series.first()
            val last = series.last()
            LineChartCard(
                title = stringResource(R.string.screens_weight_chart),
                points = series.map { it.kg.toFloat() },
                trailing = stringResource(R.string.screens_last_value, UiFormat.number(last.kg)),
                startLabel = LocalDate.ofEpochDay(first.epochDay).format(DateFmt),
                endLabel = if (series.size > 1) LocalDate.ofEpochDay(last.epochDay).format(DateFmt) else null,
            )
            RecentWeights(vm, series.takeLast(5).reversed())
        }

        SectionHeader(stringResource(R.string.screens_goals_section))
        BodyWeightSetting(vm, goals.bodyWeightKg)
        val effective = goals.withEffectiveWeight(weights)
        ProteinCard(effective.bodyWeightKg, effective.proteinRange(), goals.proteinPerKgMin, goals.proteinPerKgMax)
        WaterGoalCard(vm, goals.waterMl)
        CalorieGoalCard(vm, goals.calories)
    }
}

@Composable
private fun WeighInCard(vm: GymViewModel, series: List<BodyWeight>, today: LocalDate) {
    var kg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Double?>(null) }
    val existing = series.firstOrNull { it.epochDay == today.toEpochDay() }
    val invalid = stringResource(R.string.screens_kg_invalid)
    val added = stringResource(R.string.screens_weigh_added)

    fun write(v: Double) {
        vm.saveBodyWeight(BodyWeight(today.toEpochDay(), v))
        vm.notify(added)
        kg = ""; error = null
    }

    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_weigh_today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (existing != null) {
            Text(
                stringResource(R.string.screens_weigh_existing, UiFormat.number(existing.kg)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GymField(kg, { kg = UiFormat.sanitizeKg(it); error = null }, stringResource(R.string.screens_kg_field), keyboardType = KeyboardType.Decimal, suffix = stringResource(R.string.screens_kg), isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                stringResource(if (existing != null) R.string.screens_weigh_update else R.string.screens_weigh_add),
                onClick = {
                    val v = kg.toDoubleOrNull()
                    when {
                        v == null || v <= 0.0 || v > 500.0 -> error = invalid
                        existing != null -> confirm = v
                        else -> write(v)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    confirm?.let { v ->
        ConfirmDialog(
            title = stringResource(R.string.screens_weigh_replace_title),
            text = stringResource(R.string.screens_weigh_replace_text, UiFormat.number(existing?.kg ?: 0.0), UiFormat.number(v)),
            confirmLabel = stringResource(R.string.screens_replace),
            onConfirm = { confirm = null; write(v) },
            onDismiss = { confirm = null },
            destructive = false,
        )
    }
}

@Composable
private fun RecentWeights(vm: GymViewModel, recent: List<BodyWeight>) {
    var deleting by remember { mutableStateOf<BodyWeight?>(null) }
    AppCard(Modifier.fillMaxWidth()) {
        recent.forEach { w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.screens_kg_value, UiFormat.number(w.kg)),
                        style = NumberStyles.small,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(LocalDate.ofEpochDay(w.epochDay).format(DateFmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { deleting = w }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.screens_delete))
                }
            }
        }
    }
    deleting?.let { w ->
        ConfirmDialog(
            title = stringResource(R.string.screens_delete_record_title),
            text = stringResource(R.string.screens_delete_weight_text, UiFormat.number(w.kg), LocalDate.ofEpochDay(w.epochDay).format(DateFmt)),
            confirmLabel = stringResource(R.string.screens_delete),
            onConfirm = { deleting = null; vm.deleteBodyWeight(w) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun BodyWeightSetting(vm: GymViewModel, saved: Double?) {
    var text by remember(saved) { mutableStateOf(saved?.let(FolderLogic::kgText) ?: "") }
    val invalid = stringResource(R.string.screens_kg_invalid)
    val savedMsg = stringResource(R.string.screens_saved)
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_weight_setting), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.screens_weight_setting_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GymField(text, { text = UiFormat.sanitizeKg(it) }, stringResource(R.string.screens_kg_field), keyboardType = KeyboardType.Decimal, suffix = stringResource(R.string.screens_kg))
            SecondaryButton(
                stringResource(R.string.screens_save),
                onClick = {
                    val v = text.toDoubleOrNull()
                    if (text.isNotBlank() && v == null) vm.notify(invalid)
                    else if (vm.setBodyWeightGoal(v)) vm.notify(savedMsg)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProteinCard(weightKg: Double?, range: ClosedFloatingPointRange<Double>?, minPerKg: Double, maxPerKg: Double) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_protein_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        if (range != null && weightKg != null) {
            Text(
                stringResource(R.string.screens_protein_range, UiFormat.number(range.start, 0), UiFormat.number(range.endInclusive, 0)),
                style = NumberStyles.medium,
                color = GymTheme.colors.accent,
            )
            Text(
                stringResource(R.string.screens_protein_basis, UiFormat.number(weightKg), UiFormat.number(minPerKg, 1), UiFormat.number(maxPerKg, 1)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.screens_protein_need_weight), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WaterGoalCard(vm: GymViewModel, saved: Int) {
    var text by remember(saved) { mutableStateOf(saved.toString()) }
    val savedMsg = stringResource(R.string.screens_saved)
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_water_goal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GymField(text, { text = it.filter(Char::isDigit).take(5) }, stringResource(R.string.screens_water_field), keyboardType = KeyboardType.Number, suffix = stringResource(R.string.screens_unit_ml))
            SecondaryButton(
                stringResource(R.string.screens_save),
                onClick = { if (vm.setWaterGoal(text.toIntOrNull() ?: 0)) vm.notify(savedMsg) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CalorieGoalCard(vm: GymViewModel, saved: Int?) {
    var text by remember(saved) { mutableStateOf(saved?.toString() ?: "") }
    val savedMsg = stringResource(R.string.screens_saved)
    AppCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screens_calorie_goal), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.screens_calorie_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GymField(text, { text = it.filter(Char::isDigit).take(5) }, stringResource(R.string.screens_calorie_field), keyboardType = KeyboardType.Number, suffix = stringResource(R.string.screens_unit_kcal))
            SecondaryButton(
                stringResource(R.string.screens_save),
                onClick = { if (vm.setCalorieGoal(if (text.isBlank()) null else text.toIntOrNull() ?: 0)) vm.notify(savedMsg) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

package com.emir.gym.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.assistant.ActionPayload
import com.emir.gym.data.PendingStatus
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.Radii
import java.time.LocalDate

private val EditFormSaver = listSaver<EditForm, String>(
    save = { listOf(it.description, it.protein, it.calories, it.carbs, it.fat, it.ml, it.kg, it.minutes, it.distance, it.note) },
    restore = { EditForm(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9]) },
)

/** Sohbet icindeki oneri karti: Ekle / Duzenle / Reddet, durum rozeti, otomatik eklendi gosterimi. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionCardView(card: ActionCard, ui: AssistantUiModel, onGoTab: (Int) -> Unit) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    var editing by rememberSaveable(card.id) { mutableStateOf(false) }
    var form by rememberSaveable(card.id, stateSaver = EditFormSaver) { mutableStateOf(CardEdit.formOf(card.payload)) }
    var errors by remember(card.id) { mutableStateOf<Set<String>>(emptySet()) }
    var errorText by remember(card.id) { mutableStateOf<String?>(null) }
    val pending = card.status == PendingStatus.PENDING

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .clip(Radii.card)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.cardTop, c.cardBottom)))
            .border(1.dp, if (pending) c.accent else c.cardBorder, Radii.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(cardTitle(card.payload)),
                style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.weight(1f),
            )
            StatusBadge(card)
        }
        CardSummary(card.payload)
        if (card.payload is ActionPayload.AddMeal && pending) {
            Text(stringResource(R.string.asst_estimate_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (card.edited) {
            Text(stringResource(R.string.asst_edited_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        if (pending && editing) {
            EditFields(card.payload, form, errors, onChange = { form = it; errors = emptySet(); errorText = null })
            errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardButton(stringResource(R.string.asst_save_and_add), primary = true) {
                    when (val r = CardEdit.toPayload(card.payload, form)) {
                        is CardEdit.Result.Ok -> { editing = false; ui.confirmEdited(card, r.payload) }
                        is CardEdit.Result.Invalid -> { errors = r.fields; errorText = r.message }
                    }
                }
                CardButton(stringResource(R.string.asst_cancel_edit), primary = false) {
                    editing = false; form = CardEdit.formOf(card.payload); errors = emptySet(); errorText = null
                }
            }
        } else if (pending) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardButton(stringResource(R.string.asst_add), primary = true) { ui.confirm(card) }
                CardButton(stringResource(R.string.asst_edit), primary = false) { editing = true }
                CardButton(stringResource(R.string.asst_reject), primary = false) { ui.reject(card) }
            }
        } else if (card.status == PendingStatus.CONFIRMED) {
            TextButton(onClick = { onGoTab(ActionTargets.tabFor(card.payload)) }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(stringResource(ActionTargets.labelRes(card.payload)))
            }
        }
    }
}

private fun cardTitle(p: ActionPayload): Int = when (p) {
    is ActionPayload.AddMeal -> R.string.asst_card_meal
    is ActionPayload.AddWater -> R.string.asst_card_water
    is ActionPayload.LogWeight -> R.string.asst_card_weight
    is ActionPayload.LogActivity -> R.string.asst_card_activity
}

@Composable
private fun StatusBadge(card: ActionCard) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    val (label, bg, fg) = when {
        card.status == PendingStatus.PENDING -> Triple(R.string.asst_badge_pending, scheme.surfaceContainerHighest, scheme.onSurface)
        card.status == PendingStatus.REJECTED -> Triple(R.string.asst_badge_rejected, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        card.auto -> Triple(R.string.asst_badge_auto, c.accentSoft, c.accent)
        else -> Triple(R.string.asst_badge_added, c.accentSoft, c.accent)
    }
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        modifier = Modifier.clip(Radii.pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun dayLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (d) {
        today -> stringResource(R.string.asst_today)
        today.minusDays(1) -> stringResource(R.string.asst_yesterday)
        else -> d.format(DateFmt)
    }
}

@Composable
private fun CardSummary(p: ActionPayload) {
    val scheme = MaterialTheme.colorScheme
    fun g(v: Double) = UiFormat.number(v, 1)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (p) {
            is ActionPayload.AddMeal -> {
                Text("${p.mealType.label} · ${dayLabel(p.epochDay)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(p.description, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                Text(
                    stringResource(R.string.asst_meal_macros, g(p.proteinG), p.calories),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface,
                )
                if (p.carbsG != null || p.fatG != null) {
                    Text(
                        listOfNotNull(
                            p.carbsG?.let { stringResource(R.string.asst_carbs_value, g(it)) },
                            p.fatG?.let { stringResource(R.string.asst_fat_value, g(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                    )
                }
            }
            is ActionPayload.AddWater -> {
                Text(dayLabel(p.epochDay), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.asst_water_value, p.ml), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            }
            is ActionPayload.LogWeight -> {
                Text(dayLabel(p.epochDay), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.asst_weight_value, UiFormat.number(p.kg)), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            }
            is ActionPayload.LogActivity -> {
                Text(dayLabel(p.epochDay), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(
                    "${activityLabel(p.activity)} · ${stringResource(R.string.asst_minutes_value, p.minutes)}" +
                        (p.distanceM?.let { " · " + stringResource(R.string.asst_distance_value, it) } ?: ""),
                    style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
                )
                if (p.note.isNotEmpty()) Text(p.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditFields(p: ActionPayload, f: EditForm, errors: Set<String>, onChange: (EditForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (p) {
            is ActionPayload.AddMeal -> {
                GymField(f.description, { onChange(f.copy(description = it.take(MealForm.MAX_DESC + 20))) }, stringResource(R.string.asst_f_desc), isError = "description" in errors)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField(f.protein, { onChange(f.copy(protein = it.take(7))) }, stringResource(R.string.asst_f_protein), Modifier.weight(1f), KeyboardType.Decimal, suffix = "g", isError = "protein" in errors)
                    GymField(f.calories, { onChange(f.copy(calories = it.filter(Char::isDigit).take(5))) }, stringResource(R.string.asst_f_kcal), Modifier.weight(1f), KeyboardType.Number, suffix = "kcal", isError = "calories" in errors)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField(f.carbs, { onChange(f.copy(carbs = it.take(7))) }, stringResource(R.string.asst_f_carbs), Modifier.weight(1f), KeyboardType.Decimal, suffix = "g", isError = "carbs" in errors)
                    GymField(f.fat, { onChange(f.copy(fat = it.take(7))) }, stringResource(R.string.asst_f_fat), Modifier.weight(1f), KeyboardType.Decimal, suffix = "g", isError = "fat" in errors)
                }
            }
            is ActionPayload.AddWater ->
                GymField(f.ml, { onChange(f.copy(ml = it.filter(Char::isDigit).take(5))) }, stringResource(R.string.asst_f_ml), keyboardType = KeyboardType.Number, suffix = "ml", isError = "ml" in errors)
            is ActionPayload.LogWeight ->
                GymField(f.kg, { onChange(f.copy(kg = UiFormat.sanitizeKg(it))) }, stringResource(R.string.asst_f_kg), keyboardType = KeyboardType.Decimal, suffix = "kg", isError = "kg" in errors)
            is ActionPayload.LogActivity -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GymField(f.minutes, { onChange(f.copy(minutes = it.filter(Char::isDigit).take(4))) }, stringResource(R.string.asst_f_minutes), Modifier.weight(1f), KeyboardType.Number, isError = "minutes" in errors)
                    if (p.activity == com.emir.gym.data.ActivityType.YUZME) {
                        GymField(f.distance, { onChange(f.copy(distance = it.filter(Char::isDigit).take(6))) }, stringResource(R.string.asst_f_distance), Modifier.weight(1f), KeyboardType.Number, suffix = "m", isError = "distance" in errors)
                    }
                }
                GymField(f.note, { onChange(f.copy(note = it.take(ToolDefsNote + 20))) }, stringResource(R.string.asst_f_note), isError = "note" in errors)
            }
        }
    }
}

private const val ToolDefsNote = com.emir.gym.assistant.ToolDefs.MAX_NOTE

/** Kart dugmesi: 44 dp yuksek hap; ana eylem dolu, digerleri cerceveli. */
@Composable
private fun CardButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val c = GymTheme.colors
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(Radii.pill)
            .then(if (primary) Modifier.background(c.accentFill) else Modifier.border(1.dp, c.stroke, Radii.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            color = if (primary) c.onAccentFill else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Suppress("unused")
private val keepSpacer: @Composable () -> Unit = { Spacer(Modifier.height(0.dp)) }

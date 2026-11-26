package com.emir.gym.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.ui.theme.GymTheme
import com.emir.gym.ui.theme.LabelStyles
import com.emir.gym.ui.theme.NumberStyles
import com.emir.gym.ui.theme.Radii

/**
 * Kompakt set satırı: set numarası, kg alanı, tekrar alanı, tamam düğmesi.
 * Girdiler [UiFormat.sanitizeKg] / [UiFormat.sanitizeReps] ile temizlenip
 * yukarı iletilir; kg ondalık ayırıcısı her zaman noktadır.
 *
 * @param kgHint / [repsHint] alan boşken gri gösterilecek değer (ör. önceki antrenman).
 */
@Composable
fun SetRow(
    setNumber: Int,
    kg: String,
    onKgChange: (String) -> Unit,
    reps: String,
    onRepsChange: (String) -> Unit,
    done: Boolean,
    onDoneChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    kgHint: String? = null,
    repsHint: String? = null,
    enabled: Boolean = true,
) {
    val c = GymTheme.colors
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.inner)
            .background(if (done) c.accentSoft else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(SetColumnWidth), contentAlignment = Alignment.Center) {
            Text(
                setNumber.toString(),
                style = NumberStyles.small,
                color = if (done) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
        NumberField(
            value = kg,
            onValueChange = { onKgChange(UiFormat.sanitizeKg(it)) },
            unit = stringResource(R.string.ui_kg_unit),
            hint = kgHint,
            keyboardType = KeyboardType.Decimal,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = reps,
            onValueChange = { onRepsChange(UiFormat.sanitizeReps(it)) },
            unit = stringResource(R.string.ui_reps_unit),
            hint = repsHint,
            keyboardType = KeyboardType.Number,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        DoneToggle(done = done, onDoneChange = onDoneChange, enabled = enabled)
    }
}

/** Set satırının sütun başlıkları; [SetRow] ile aynı hizada. */
@Composable
fun SetRowHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(stringResource(R.string.ui_set_col), Modifier.width(SetColumnWidth), TextAlign.Center)
        HeaderCell(stringResource(R.string.ui_kg_col), Modifier.weight(1f), TextAlign.Start, startPad = 12.dp)
        HeaderCell(stringResource(R.string.ui_reps_col), Modifier.weight(1f), TextAlign.Start, startPad = 12.dp)
        Box(Modifier.width(ToggleSize))
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, modifier: Modifier, align: TextAlign, startPad: Dp = 0.dp) {
    Text(
        text,
        style = LabelStyles.capsSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier.padding(start = startPad),
        maxLines = 1,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    hint: String?,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val c = GymTheme.colors
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = NumberStyles.small.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(Radii.inner)
                    .background(scheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .border(1.dp, if (focused) c.accent else Color.Transparent, Radii.inner)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && hint != null) {
                        Text(hint, style = NumberStyles.small, color = scheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                    }
                    inner()
                }
                Text(unit, style = NumberStyles.unit, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        },
    )
}

@Composable
private fun DoneToggle(done: Boolean, onDoneChange: (Boolean) -> Unit, enabled: Boolean) {
    val c = GymTheme.colors
    val description = stringResource(if (done) R.string.ui_set_undo else R.string.ui_set_done)
    Box(
        modifier = Modifier
            .size(ToggleSize)
            .clip(CircleShape)
            .toggleable(value = done, enabled = enabled, role = Role.Checkbox, onValueChange = onDoneChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (done) c.accentFill else Color.Transparent)
                .border(1.5.dp, if (done) c.accentFill else c.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                GymIcons.Check,
                contentDescription = null,
                tint = if (done) c.onAccentFill else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private val SetColumnWidth: Dp = 28.dp
private val ToggleSize: Dp = 44.dp

@PreviewLightDark
@Composable
private fun SetRowPreview() = PreviewBox {
    Column {
        SetRowHeader()
        var kg by remember { mutableStateOf("40") }
        var reps by remember { mutableStateOf("8") }
        var done by remember { mutableStateOf(true) }
        SetRow(1, kg, { kg = it }, reps, { reps = it }, done, { done = it })
        var kg2 by remember { mutableStateOf("") }
        var reps2 by remember { mutableStateOf("") }
        var done2 by remember { mutableStateOf(false) }
        SetRow(2, kg2, { kg2 = it }, reps2, { reps2 = it }, done2, { done2 = it }, kgHint = "40", repsHint = "8")
    }
}

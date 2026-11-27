package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.ActivityType
import com.emir.gym.ui.theme.Radii
import com.emir.gym.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
val ShortDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

/** Geniş ekranda içerik en fazla bu genişlikte ortalanır. */
val MaxContentWidth = 640.dp

/** Kaydırılabilir, ortalanmış ekran gövdesi. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap),
            content = content,
        )
    }
}

@Composable
fun weekdayName(weekday: Int): String = stringArrayResource(R.array.screens_weekday_full)[(weekday - 1).coerceIn(0, 6)]

@Composable
fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.YUZME -> stringResource(R.string.screens_act_swim)
    ActivityType.SAUNA -> stringResource(R.string.screens_act_sauna)
    ActivityType.BUHAR -> stringResource(R.string.screens_act_steam)
}

@Composable
fun dateWithWeekday(date: LocalDate): String = "${date.format(DateFmt)} · ${weekdayName(date.dayOfWeek.value)}"

/** Silme ve benzeri geri alınamaz işlemler için onay. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Form alanı: tek satır, yuvarlak köşe. */
@Composable
fun GymField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    suffix: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        singleLine = singleLine,
        isError = isError,
        suffix = suffix?.let { { Text(it) } },
        shape = Radii.inner,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

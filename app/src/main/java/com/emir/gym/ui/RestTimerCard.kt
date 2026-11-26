package com.emir.gym.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.emir.gym.R
import com.emir.gym.data.RestTimer
import kotlinx.coroutines.delay

/** Set arasi geri sayim. Kalan sure bitis zaman damgasindan hesaplanir, ekran kapansa da dogru kalir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerCard(
    endAt: Long?,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt != null) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    var custom by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (endAt == null) {
                Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RestTimer.PRESETS.forEach { s ->
                        OutlinedButton(onClick = { onStart(s) }) { Text(stringResource(R.string.rest_seconds, s)) }
                    }
                    OutlinedButton(onClick = { custom = true }) { Text(stringResource(R.string.rest_custom)) }
                }
            } else {
                val left = RestTimer.remainingSeconds(endAt, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.rest_running, RestTimer.format(left)),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.rest_cancel)) }
                }
            }
        }
    }

    if (custom) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text(stringResource(R.string.rest_custom_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        text, { text = it; error = false },
                        label = { Text(stringResource(R.string.rest_custom_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error) Text(stringResource(R.string.rest_custom_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = RestTimer.parseCustom(text)
                    if (s == null) error = true else { custom = false; onStart(s) }
                }) { Text(stringResource(R.string.rest_start)) }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

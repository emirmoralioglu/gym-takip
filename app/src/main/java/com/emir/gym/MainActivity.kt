package com.emir.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emir.gym.ui.DayFolderScreen
import com.emir.gym.ui.MaxContentWidth
import com.emir.gym.ui.NutritionScreen
import com.emir.gym.ui.ProgressScreen
import com.emir.gym.ui.RestPillHost
import com.emir.gym.ui.SettingsScreen
import com.emir.gym.ui.WeeklyScreen
import com.emir.gym.ui.WorkoutScreen
import com.emir.gym.ui.components.GymBottomBar
import com.emir.gym.ui.components.GymIcons
import com.emir.gym.ui.components.GymNavItem
import com.emir.gym.ui.components.UiFormat
import com.emir.gym.ui.theme.GymTheme

private const val SETTINGS_TAB = 4

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GymTheme { GymApp(vm) } }
    }
}

@Composable
private fun GymApp(vm: GymViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Açık gün klasörünün hafta günü (1..7); null = Antrenman ana ekranı.
    var folder by rememberSaveable { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val error by vm.error.collectAsState()
    val record by vm.newRecord.collectAsState()
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    val items = listOf(
        GymNavItem(stringResource(R.string.screens_tab_workout), GymIcons.Today),
        GymNavItem(stringResource(R.string.tab_weekly), GymIcons.Week),
        GymNavItem(stringResource(R.string.tab_progress), GymIcons.Trend),
        GymNavItem(stringResource(R.string.tab_nutrition), GymIcons.Meal),
        GymNavItem(stringResource(R.string.tab_settings), GymIcons.Sliders),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                RestPillHost(vm)
                GymBottomBar(
                    items = items,
                    selectedIndex = tab,
                    onSelect = {
                        // Seçili Antrenman sekmesine tekrar dokunmak klasörden ana ekrana döner.
                        if (it == 0 && tab == 0) folder = null
                        tab = it
                    },
                )
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> {
                val f = folder
                if (f == null) WorkoutScreen(vm, onOpenDay = { folder = it }, modifier = m)
                else DayFolderScreen(vm, f, onBack = { folder = null }, modifier = m)
            }
            1 -> WeeklyScreen(vm, m)
            2 -> ProgressScreen(vm, m)
            3 -> NutritionScreen(vm, onOpenSettings = { tab = SETTINGS_TAB }, modifier = m)
            else -> SettingsScreen(vm, m)
        }
    }
    record?.let { r ->
        AlertDialog(
            onDismissRequest = vm::clearNewRecord,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(GymIcons.Trophy, null, tint = GymTheme.colors.pr) },
            title = { Text(stringResource(R.string.record_title)) },
            text = {
                Column {
                    Text(r.exercise, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (r.weightBroken) Text(stringResource(R.string.record_weight, UiFormat.number(r.weightKg), UiFormat.number(r.prevWeightKg)))
                    if (r.oneRmBroken) Text(stringResource(R.string.record_1rm, UiFormat.number(r.oneRm, 1), UiFormat.number(r.prevOneRm, 1)))
                }
            },
            confirmButton = { TextButton(onClick = vm::clearNewRecord) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

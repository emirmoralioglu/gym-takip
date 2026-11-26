package com.emir.gym

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import com.emir.gym.ui.DailyScreen
import com.emir.gym.ui.ProgressScreen
import com.emir.gym.ui.fmtKg
import com.emir.gym.ui.SettingsScreen
import com.emir.gym.ui.WeeklyScreen
import com.emir.gym.ui.theme.GymTheme

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val error by vm.error.collectAsState()
                val entries by vm.entries.collectAsState()
                val custom by vm.customExercises.collectAsState()
                val restEndAt by vm.restEndAt.collectAsState()
                val record by vm.newRecord.collectAsState()
                LaunchedEffect(error) {
                    error?.let { snackbar.showSnackbar(it); vm.clearError() }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Edit, null) }, label = { Text(stringResource(R.string.tab_daily)) })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text(stringResource(R.string.tab_weekly)) })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Star, null) }, label = { Text(stringResource(R.string.tab_progress)) })
                            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.tab_settings)) })
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    when (tab) {
                        0 -> DailyScreen(
                            entries, custom, restEndAt,
                            vm::save, vm::delete, vm::copyEntries, vm::addCustomExercise,
                            vm::startRest, vm::cancelRest, vm::notify, m,
                        )
                        1 -> WeeklyScreen(entries, m)
                        2 -> ProgressScreen(entries, m)
                        else -> SettingsScreen(m)
                    }
                }
                record?.let { r ->
                    AlertDialog(
                        onDismissRequest = vm::clearNewRecord,
                        icon = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text(stringResource(R.string.record_title)) },
                        text = {
                            Column {
                                Text(r.exercise, style = MaterialTheme.typography.titleMedium)
                                if (r.weightBroken) Text(stringResource(R.string.record_weight, fmtKg(r.weightKg), fmtKg(r.prevWeightKg)))
                                if (r.oneRmBroken) Text(stringResource(R.string.record_1rm, fmtKg(Math.round(r.oneRm * 10) / 10.0), fmtKg(Math.round(r.prevOneRm * 10) / 10.0)))
                            }
                        },
                        confirmButton = { TextButton(onClick = vm::clearNewRecord) { Text(stringResource(R.string.action_ok)) } },
                    )
                }
            }
        }
    }
}

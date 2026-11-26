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
import com.emir.gym.ui.DailyScreen
import com.emir.gym.ui.SettingsScreen
import com.emir.gym.ui.WeeklyScreen

class MainActivity : ComponentActivity() {
    private val vm: GymViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val snackbar = remember { SnackbarHostState() }
                val error by vm.error.collectAsState()
                val entries by vm.entries.collectAsState()
                LaunchedEffect(error) {
                    error?.let { snackbar.showSnackbar(it); vm.clearError() }
                }
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Edit, null) }, label = { Text("Günlük") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.DateRange, null) }, label = { Text("Haftalık") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Ayarlar") })
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    when (tab) {
                        0 -> DailyScreen(entries, vm::save, vm::delete, m)
                        1 -> WeeklyScreen(entries, m)
                        else -> SettingsScreen(m)
                    }
                }
            }
        }
    }
}

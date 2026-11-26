package com.emir.gym.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emir.gym.GymViewModel
import com.emir.gym.data.RestTimer
import com.emir.gym.ui.components.RestTimerPill
import kotlinx.coroutines.delay

/** Çalışan ya da duraklatılmış dinlenme sayacı varsa alt çubuğun üstünde RestTimerPill gösterir. */
@Composable
fun RestPillHost(vm: GymViewModel) {
    val endAt by vm.restEndAt.collectAsState()
    val pausedMs by vm.restPausedLeftMs.collectAsState()
    val total by vm.restTotalSeconds.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt != null) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val end = endAt
    val paused = pausedMs
    if (end == null && paused == null) return
    val left = if (end != null) RestTimer.remainingSeconds(end, now) else ((paused!! + 999) / 1000).toInt()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        RestTimerPill(
            remainingSeconds = left,
            totalSeconds = maxOf(total, left),
            running = end != null,
            onToggle = { if (end != null) vm.pauseRest() else vm.resumeRest() },
            onAddTime = { vm.addRest(30) },
            onDismiss = vm::cancelRest,
            modifier = Modifier.widthIn(max = MaxContentWidth),
        )
    }
}

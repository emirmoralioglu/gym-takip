package com.emir.gym.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Dinlenme sayaci mantigi: bitis zaman damgasi (ms) tabanli, ekran kapansa da dogru. */
object RestTimer {
    val PRESETS = listOf(30, 60, 90, 120)
    const val MAX_SECONDS = 3600

    /** Kalan tam saniye (yukari yuvarlanir); bittiyse 0. */
    fun remainingSeconds(endAtMs: Long, nowMs: Long): Int {
        val ms = endAtMs - nowMs
        if (ms <= 0) return 0
        return ((ms + 999) / 1000).toInt()
    }

    fun format(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /** Ozel sure: 1..3600 saniye. Gecersizse null. */
    fun parseCustom(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..MAX_SECONDS }

    /** Titresim; cihazda titresim yoksa sessizce gecer (sayac metni yine de gorunur). */
    fun vibrate(context: Context) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
            }
        } catch (_: SecurityException) {
            // Izin yoksa titresim atlanir; geri bildirim ekrandaki mesajla verilir.
        }
    }
}

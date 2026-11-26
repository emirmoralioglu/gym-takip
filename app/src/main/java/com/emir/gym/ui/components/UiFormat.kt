package com.emir.gym.ui.components

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Görüntü biçimlendirme yardımcıları. Ondalık ayırıcı her yerde nokta,
 * binlik ayırıcı bölünmez boşluktur ("6 393").
 */
object UiFormat {
    private const val GROUP = ' '

    /** 6393 -> "6 393". */
    fun grouped(value: Long): String {
        val digits = abs(value).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, ch ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(GROUP)
            sb.append(ch)
        }
        return if (value < 0) "-$sb" else sb.toString()
    }

    /** 82.0 -> "82", 82.5 -> "82.5", 82.25 -> "82.25". Binlik gruplama yapılır. */
    fun number(value: Double, maxDecimals: Int = 2): String {
        require(maxDecimals in 0..3)
        val factor = when (maxDecimals) { 0 -> 1L; 1 -> 10L; 2 -> 100L; else -> 1000L }
        val scaled = (value * factor).roundToLong()
        val whole = scaled / factor
        var frac = abs(scaled % factor).toString().padStart(maxDecimals, '0')
        frac = frac.trimEnd('0')
        val sign = if (scaled < 0 && whole == 0L) "-" else ""
        return sign + grouped(whole) + if (frac.isEmpty()) "" else ".$frac"
    }

    /** Saniyeyi "dd:ss" olarak yazar (en fazla 99:59 gösterilir). */
    fun clock(totalSeconds: Int): String {
        val s = totalSeconds.coerceIn(0, 99 * 60 + 59)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    /**
     * Kullanıcının kg girdisini temizler: virgül noktaya çevrilir, yalnızca
     * rakam ve tek nokta kalır, en fazla 4 tam ve 2 ondalık basamak.
     */
    fun sanitizeKg(input: String): String {
        val sb = StringBuilder()
        var dot = false
        var intDigits = 0
        var decDigits = 0
        for (raw in input) {
            val ch = if (raw == ',') '.' else raw
            when {
                ch == '.' && !dot -> { dot = true; if (intDigits == 0) { sb.append('0'); intDigits = 1 }; sb.append('.') }
                ch.isDigit() && !dot && intDigits < 4 -> { sb.append(ch); intDigits++ }
                ch.isDigit() && dot && decDigits < 2 -> { sb.append(ch); decDigits++ }
            }
        }
        return sb.toString()
    }

    /** Tekrar girdisi: yalnızca rakam, en fazla 3 basamak. */
    fun sanitizeReps(input: String): String = input.filter { it.isDigit() }.take(3)
}

package com.emir.gym.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material renk şemasında karşılığı olmayan, uygulamaya özel renkler.
 * Bileşenler bunlara [GymTheme.colors] üzerinden erişir.
 */
@Immutable
data class GymColors(
    /** Vurgu: metin, çizgi, ikon (zemin üstünde okunur). */
    val accent: Color,
    /** Vurgu dolgusu: dolu düğme, tamamlanan gün. */
    val accentFill: Color,
    /** [accentFill] üstündeki metin/ikon. */
    val onAccentFill: Color,
    /** Vurgunun çok hafif zemini (tamamlanan set satırı vb.). */
    val accentSoft: Color,
    /** Rekor / başarı sinyali. */
    val pr: Color,
    val onPr: Color,
    val prSoft: Color,
    /** Kart degradesi: üst ve alt ton. */
    val cardTop: Color,
    val cardBottom: Color,
    /** Kartın ince çerçevesi (dekoratif). */
    val cardBorder: Color,
    /** İlerleme izi, boş çubuk. */
    val track: Color,
    /** Etkileşimli ince çizgiler (planlı gün halkası, giriş alanı çerçevesi); >= 3:1. */
    val stroke: Color,
    /** Grafik çizgisi ve degrade dolgunun üst tonu. */
    val chartLine: Color,
    val chartFillTop: Color,
    val chartGrid: Color,
    /** Alt gezinme çubuğu zemini. */
    val barBackground: Color,
)

internal val DarkGymColors = GymColors(
    accent = Lime,
    accentFill = Lime,
    onAccentFill = Ink,
    accentSoft = Color(0x1FC6FF3D),
    pr = DarkTertiary,
    onPr = Ink,
    prSoft = Color(0x26FFB938),
    cardTop = Color(0xFF1C1C1F),
    cardBottom = Color(0xFF0D0D0F),
    cardBorder = Color(0xFF2A2A2F),
    track = Color(0xFF26262B),
    stroke = DarkOutline,
    chartLine = Color(0xFFF4F4F5),
    chartFillTop = Color(0x59C6FF3D),
    chartGrid = Color(0xFF1F1F23),
    barBackground = Color(0xFF000000),
)

internal val LightGymColors = GymColors(
    accent = LightPrimary,
    accentFill = Lime,
    onAccentFill = LimeInk,
    accentSoft = Color(0x2E9BD40F),
    pr = LightTertiary,
    onPr = Color(0xFFFFFFFF),
    prSoft = Color(0x268A5300),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFF4F4F6),
    cardBorder = Color(0xFFE2E2E6),
    track = Color(0xFFE4E4E7),
    stroke = LightOutline,
    chartLine = Color(0xFF0A0A0B),
    chartFillTop = Color(0x6699D10A),
    chartGrid = Color(0xFFEDEDF0),
    barBackground = Color(0xFFFFFFFF),
)

val LocalGymColors = staticCompositionLocalOf { DarkGymColors }

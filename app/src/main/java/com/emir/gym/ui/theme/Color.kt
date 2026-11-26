package com.emir.gym.ui.theme

import androidx.compose.ui.graphics.Color

// Görsel dil: neredeyse saf siyah (OLED) zemin, beyaz/gri metin,
// tek vurgu rengi limon yeşili. Rekor (PR) için kehribar, hata için mercan kırmızısı.
// Kontrast oranları (WCAG 2.x göreli parlaklık formülü):
//   Koyu: metin F4F4F5 / zemin 050505 = 18.5:1, ikincil metin A1A1AA / 050505 = 7.9:1,
//         A1A1AA / kart 1A1A1D = 6.8:1, vurgu C6FF3D / 050505 = 17.3:1,
//         siyah / vurgu = 16.8:1, PR FFB938 / 050505 = 11.9:1, hata FF6B6B / 050505 = 7.3:1,
//         kesik halka 6B6B73 / 050505 = 3.9:1 (UI bileşeni, >= 3:1).
//   Açık: metin 0A0A0B / F7F7F8 = 18.5:1, ikincil 52525B / F7F7F8 = 7.2:1,
//         vurgu metni 3F5A00 / F7F7F8 = 7.3:1, 141F00 / C6FF3D = 14.5:1,
//         PR 8A5300 / F7F7F8 = 5.9:1, hata C62828 / F7F7F8 = 5.3:1,
//         kesik halka 8E8E96 / F7F7F8 = 3.0:1.

// Ortak tonlar
internal val Lime = Color(0xFFC6FF3D)
internal val LimeInk = Color(0xFF141F00)
internal val Ink = Color(0xFF0A0A0A)

// Koyu tema (ana tema)
internal val DarkBackground = Color(0xFF050505)
internal val DarkOnSurface = Color(0xFFF4F4F5)
internal val DarkOnSurfaceVariant = Color(0xFFA1A1AA)
internal val DarkPrimary = Lime
internal val DarkOnPrimary = Ink
internal val DarkPrimaryContainer = Color(0xFF2B3A05)
internal val DarkOnPrimaryContainer = Color(0xFFDDFF8F)
internal val DarkSecondary = Color(0xFFE4E4E7)
internal val DarkOnSecondary = Ink
internal val DarkSecondaryContainer = Color(0xFF27272A)
internal val DarkOnSecondaryContainer = Color(0xFFF4F4F5)
internal val DarkTertiary = Color(0xFFFFB938)
internal val DarkOnTertiary = Ink
internal val DarkTertiaryContainer = Color(0xFF3D2A00)
internal val DarkOnTertiaryContainer = Color(0xFFFFDDA0)
internal val DarkError = Color(0xFFFF6B6B)
internal val DarkOnError = Ink
internal val DarkErrorContainer = Color(0xFF4A0E0E)
internal val DarkOnErrorContainer = Color(0xFFFFD6D6)
internal val DarkOutline = Color(0xFF6B6B73)
internal val DarkOutlineVariant = Color(0xFF2A2A2F)
internal val DarkSurfaceContainerLowest = Color(0xFF000000)
internal val DarkSurfaceContainerLow = Color(0xFF0E0E10)
internal val DarkSurfaceContainer = Color(0xFF131315)
internal val DarkSurfaceContainerHigh = Color(0xFF1A1A1D)
internal val DarkSurfaceContainerHighest = Color(0xFF232327)
internal val DarkSurfaceBright = Color(0xFF2C2C31)

// Açık tema
internal val LightBackground = Color(0xFFF7F7F8)
internal val LightOnSurface = Color(0xFF0A0A0B)
internal val LightOnSurfaceVariant = Color(0xFF52525B)
internal val LightPrimary = Color(0xFF3F5A00)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Lime
internal val LightOnPrimaryContainer = LimeInk
internal val LightSecondary = Color(0xFF27272A)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFE4E4E7)
internal val LightOnSecondaryContainer = Color(0xFF0A0A0B)
internal val LightTertiary = Color(0xFF8A5300)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFFE2B0)
internal val LightOnTertiaryContainer = Color(0xFF2B1A00)
internal val LightError = Color(0xFFC62828)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFE0E0)
internal val LightOnErrorContainer = Color(0xFF410002)
internal val LightOutline = Color(0xFF8E8E96)
internal val LightOutlineVariant = Color(0xFFE2E2E6)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFFCFCFD)
internal val LightSurfaceContainer = Color(0xFFF2F2F4)
internal val LightSurfaceContainerHigh = Color(0xFFECECEF)
internal val LightSurfaceContainerHighest = Color(0xFFE4E4E7)
internal val LightSurfaceDim = Color(0xFFE4E4E7)

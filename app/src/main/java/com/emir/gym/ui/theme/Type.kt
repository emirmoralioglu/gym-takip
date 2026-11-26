package com.emir.gym.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Sistem fontu (Samsung'da One UI Sans, diğer cihazlarda Roboto) kullanılır;
// gömülü font yok. Başlıklar daha kalın, gövde metni varsayılan.
private val Base = TextStyle(fontFamily = FontFamily.Default)

val GymTypography = Typography(
    displayLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = Base.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// Sayı ağırlıklı alanlar (kg, tekrar, set) için tabular rakamlar:
// "tnum" her rakamı eşit genişlikte çizer, böylece sütunlar hizalı kalır
// ve değer değişince metin zıplamaz.
private const val TABULAR = "tnum"

object NumberStyles {
    /** Büyük vurgu sayısı (ör. haftalık toplam). */
    val large = Base.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = TABULAR)

    /** Kart içi ana değer (ör. kg). */
    val medium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR)

    /** Liste/tablo hücresi. */
    val small = Base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR)

    /** Birim etiketi (kg, tekrar) - sayının yanında daha küçük. */
    val unit = Base.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp)
}

/** Var olan herhangi bir stile tabular rakam ekler. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

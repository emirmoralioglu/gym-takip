package com.emir.gym.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

// Sistem fontu (Samsung'da One UI Sans, diğer cihazlarda Roboto); gömülü font yok.
// Hiyerarşi ağırlık ve boyut farkıyla kurulur: büyük, sıkı başlıklar;
// küçük, açık aralıklı büyük harf etiketler; gri ikincil metin.
private val Base = TextStyle(fontFamily = FontFamily.Default)

val GymTypography = Typography(
    displayLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp),
    displayMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp),
    displaySmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.75).sp),
    headlineLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.25).sp),
    titleLarge = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = Base.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

// Sayı ağırlıklı alanlar (kg, tekrar, set) için tabular rakamlar:
// "tnum" her rakamı eşit genişlikte çizer; sütunlar hizalı kalır,
// değer değişince metin zıplamaz.
private const val TABULAR = "tnum"

object NumberStyles {
    /** Kahraman sayı (ör. haftalık tonaj). */
    val hero = Base.copy(fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.25).sp, fontFeatureSettings = TABULAR)

    /** Büyük vurgu sayısı (ör. kart değeri). */
    val large = Base.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.75).sp, fontFeatureSettings = TABULAR)

    /** Kart içi ana değer (ör. kg). */
    val medium = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp, fontFeatureSettings = TABULAR)

    /** Liste/tablo hücresi, set satırı. */
    val small = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR)

    /** Birim etiketi (kg, tekrar): sayının yanında daha küçük. */
    val unit = Base.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp)
}

object LabelStyles {
    /** Küçük büyük harf etiket (bölüm başlığı, kart etiketi). */
    val caps = Base.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)

    /** Daha da küçük büyük harf etiket (gün adı, sütun başlığı). */
    val capsSmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.sp)
}

/** Var olan herhangi bir stile tabular rakam ekler. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

private val TurkishLocale: Locale = Locale.forLanguageTag("tr-TR")

/** Türkçe kurallarıyla büyük harf: "i" -> "İ", "ı" -> "I". */
fun String.upperTr(): String = uppercase(TurkishLocale)

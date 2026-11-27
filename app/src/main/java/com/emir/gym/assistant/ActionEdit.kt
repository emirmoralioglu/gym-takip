package com.emir.gym.assistant

import com.emir.gym.ui.MealForm
import com.emir.gym.ui.NutritionUi
import java.time.LocalDate

/**
 * Kullanicinin duzeltip onayladigi oneri icin saf dogrulama. Sinirlar [ToolValidator] ve formlarla AYNIDIR
 * (protein <= 300, karbonhidrat <= 1000, yag <= 500, kalori <= 5000, aciklama <= 80, su <= 5000).
 */
/**
 * Model ciktisindaki gorunmez / yon degistiren karakterleri atar (kartta gorunen = kayitta olan):
 * kontrol (Cc: satir sonu, sekme dahil -> bosluk), bicim (Cf: U+200E/F, U+202A-E, U+2066-2069, sifir genislik, BOM),
 * satir/paragraf ayiricilari. Sonra bosluklar sadelestirilir ve kirpilir.
 */
object TextClean {
    fun clean(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (Character.getType(ch).toByte()) {
                Character.FORMAT -> {}
                Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }
}

object ActionEdit {
    /** @return null = gecerli; aksi halde kullaniciya gosterilecek kisa Turkce hata. */
    fun check(p: ActionPayload, today: LocalDate = LocalDate.now()): String? {
        val day = LocalDate.ofEpochDay(p.epochDay)
        if (day.isAfter(today)) return "Gelecek tarihe kayıt eklenemez."
        if (day.isBefore(today.minusYears(5))) return "Tarih çok eski."
        fun bad(v: Double, max: Double) = !v.isFinite() || v < 0 || v > max
        return when (p) {
            is ActionPayload.AddMeal -> when {
                TextClean.clean(p.description).isEmpty() -> "Açıklama boş olamaz."
                TextClean.clean(p.description).length > MealForm.MAX_DESC -> "Açıklama en fazla ${MealForm.MAX_DESC} karakter olabilir."
                bad(p.proteinG, MealForm.MAX_PROTEIN) -> "Protein 0 ile ${MealForm.MAX_PROTEIN.toInt()} g arasında olmalı."
                p.calories < 0 || p.calories > MealForm.MAX_KCAL -> "Kalori 0 ile ${MealForm.MAX_KCAL} arasında olmalı."
                p.carbsG != null && bad(p.carbsG, MealForm.MAX_CARBS) -> "Karbonhidrat 0 ile ${MealForm.MAX_CARBS.toInt()} g arasında olmalı."
                p.fatG != null && bad(p.fatG, MealForm.MAX_FAT) -> "Yağ 0 ile ${MealForm.MAX_FAT.toInt()} g arasında olmalı."
                else -> null
            }
            is ActionPayload.AddWater ->
                if (p.ml < 1 || p.ml > NutritionUi.MAX_WATER_ML) "Su 1 ile ${NutritionUi.MAX_WATER_ML} ml arasında olmalı." else null
            is ActionPayload.LogWeight ->
                if (!p.kg.isFinite() || p.kg <= 0.0 || p.kg > ToolDefs.MAX_KG) "Kilo 0 ile ${ToolDefs.MAX_KG} kg arasında olmalı." else null
            is ActionPayload.LogActivity -> when {
                p.minutes < 1 || p.minutes > ToolDefs.MAX_MINUTES -> "Süre 1 ile ${ToolDefs.MAX_MINUTES} dakika arasında olmalı."
                p.distanceM != null && (p.distanceM < 1 || p.distanceM > ToolDefs.MAX_DISTANCE_M) -> "Mesafe 1 ile ${ToolDefs.MAX_DISTANCE_M} m arasında olmalı."
                p.distanceM != null && p.activity != com.emir.gym.data.ActivityType.YUZME -> "Mesafe yalnızca yüzme için girilebilir."
                TextClean.clean(p.note).length > ToolDefs.MAX_NOTE -> "Not en fazla ${ToolDefs.MAX_NOTE} karakter olabilir."
                else -> null
            }
        }
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    /** Kaydi [ToolValidator] ile AYNI bicimde temizler: metinler, protein/karb/yag 1 hane, kilo 2 hane. */
    fun normalize(p: ActionPayload): ActionPayload = when (p) {
        is ActionPayload.AddMeal -> p.copy(
            description = TextClean.clean(p.description),
            proteinG = if (p.proteinG.isFinite()) round1(p.proteinG) else p.proteinG,
            carbsG = p.carbsG?.let { if (it.isFinite()) round1(it) else it },
            fatG = p.fatG?.let { if (it.isFinite()) round1(it) else it },
        )
        is ActionPayload.LogActivity -> p.copy(note = TextClean.clean(p.note))
        is ActionPayload.LogWeight -> if (p.kg.isFinite()) p.copy(kg = Math.round(p.kg * 100) / 100.0) else p
        else -> p
    }
}

/**
 * pending_actions.resultRef: "meal:12" ya da duzenlenerek eklendiyse "meal:12|{payload JSON}".
 * (Sema degismedigi icin duzenlenmis veri ayni alanda tasinir; oneri kartinin gercekte eklenen degeri gostermesi icin.)
 */
object ActionRef {
    private const val SEP = '|'

    fun withEdit(ref: String, edited: ActionPayload): String = ref + SEP + ToolValidator.encode(edited)

    data class Parsed(val ref: String, val editedJson: String?)

    fun parse(raw: String?): Parsed? {
        if (raw == null) return null
        val i = raw.indexOf(SEP)
        return if (i < 0) Parsed(raw, null) else Parsed(raw.substring(0, i), raw.substring(i + 1))
    }

    fun edited(type: String, raw: String?): ActionPayload? =
        parse(raw)?.editedJson?.let { ToolValidator.decode(type, it) }
}

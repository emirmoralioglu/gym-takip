package com.emir.gym.ui

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.data.Meal
import com.emir.gym.data.MealType

/** Beslenme ekraninin saf mantigi (arayuzden bagimsiz, birim testli). */
object NutritionUi {
    /** Yuzme/sauna gunlerinde su hedefine eklenen miktar (ml). */
    const val WATER_BONUS_ML = 500
    const val RECENT_LIMIT = 8
    const val MAX_WATER_ML = 5000

    // ---- Gun gezinme ----

    /** [delta] gun kaydirir; bugunun otesine gecmez. */
    fun shiftDay(current: Long, delta: Int, today: Long): Long = (current + delta).coerceAtMost(today)

    fun canGoNext(current: Long, today: Long): Boolean = current < today

    enum class DayLabel { TODAY, YESTERDAY, DATE }

    fun dayLabel(day: Long, today: Long): DayLabel = when (day) {
        today -> DayLabel.TODAY
        today - 1 -> DayLabel.YESTERDAY
        else -> DayLabel.DATE
    }

    /** Secilen gun gelecekse bugune kirpilir. */
    fun clampToToday(day: Long, today: Long): Long = day.coerceAtMost(today)

    /** DatePicker UTC milisaniyesinden gun. */
    fun utcMillisToEpochDay(ms: Long): Long = Math.floorDiv(ms, 86_400_000L)

    // ---- Su hedefi ----

    data class WaterGoal(val baseMl: Int, val bonusMl: Int) {
        val totalMl: Int get() = baseMl + bonusMl
    }

    /** O gun yuzme ya da sauna kaydi varsa hedef 500 ml artar (buhar saymaz). */
    fun waterGoal(baseMl: Int, epochDay: Long, activities: List<ActivityLog>): WaterGoal {
        val bonus = activities.any { it.epochDay == epochDay && (it.type == ActivityType.YUZME || it.type == ActivityType.SAUNA) }
        return WaterGoal(baseMl, if (bonus) WATER_BONUS_ML else 0)
    }

    // ---- Durum metni ----

    enum class StatusKind { REMAINING, REACHED, OVER }
    data class Status(val kind: StatusKind, val amount: Double)

    /**
     * [low] hedefin alt siniri; [high] varsa ust sinir (protein araligi), yoksa hedef tek degerdir.
     * Altinda: kalan; [low]..[high]: ulasildi; ustunde: asim.
     */
    fun status(current: Double, low: Double, high: Double? = null): Status {
        val top = high ?: low
        return when {
            current < low -> Status(StatusKind.REMAINING, low - current)
            current <= top -> Status(StatusKind.REACHED, 0.0)
            else -> Status(StatusKind.OVER, current - top)
        }
    }

    // ---- Ogunler ----

    fun mealsOfDay(meals: List<Meal>, epochDay: Long): List<Meal> =
        meals.filter { it.epochDay == epochDay }.sortedBy { it.id }

    /** Tur sirasiyla (Kahvalti, Ogle, Aksam, Ara) bos olmayan gruplar. */
    fun groupByType(meals: List<Meal>): List<Pair<MealType, List<Meal>>> =
        MealType.entries.mapNotNull { t -> meals.filter { it.mealType == t }.takeIf { it.isNotEmpty() }?.let { t to it } }

    /** Saate gore varsayilan tur. */
    fun defaultMealType(hour: Int): MealType = when {
        hour < 11 -> MealType.KAHVALTI
        hour < 15 -> MealType.OGLE
        hour < 18 -> MealType.ARA
        else -> MealType.AKSAM
    }

    private fun mealKey(m: Meal) =
        listOf(m.description.trim().lowercase(), m.proteinG, m.calories, m.carbsG, m.fatG)

    /**
     * Son kullanilan benzersiz ogunler (aciklama + makrolar). Yeniden eskiye; ayni anahtarda
     * en yeni kayit kalir. Aciklamasi bos olanlar atlanir. Girdi sirasi onemsizdir.
     */
    fun recentMeals(meals: List<Meal>, limit: Int = RECENT_LIMIT): List<Meal> =
        meals.filter { it.description.isNotBlank() }
            .sortedWith(compareByDescending<Meal> { it.epochDay }.thenByDescending { it.id })
            .distinctBy(::mealKey)
            .take(limit)

    /** Hizli eklemede kopya: yeni kayit, hedef gun. */
    fun copyTo(meal: Meal, epochDay: Long): Meal = meal.copy(id = 0, epochDay = epochDay)

    // ---- Haftalik protein ----

    data class DayTotal(val epochDay: Long, val proteinG: Double)

    data class WeekProtein(val days: List<DayTotal>, val averageG: Double?) {
        val hasData: Boolean get() = averageG != null
    }

    /** [endDay] dahil son 7 gun, eskiden yeniye. Ortalama yalnizca ogun kaydi olan gunlerden. */
    fun weeklyProtein(meals: List<Meal>, endDay: Long): WeekProtein {
        val range = (endDay - 6..endDay).toList()
        val days = range.map { d -> DayTotal(d, meals.filter { it.epochDay == d }.sumOf { it.proteinG }) }
        val logged = range.filter { d -> meals.any { it.epochDay == d } }.toSet()
        val avg = if (logged.isEmpty()) null else days.filter { it.epochDay in logged }.sumOf { it.proteinG } / logged.size
        return WeekProtein(days, avg)
    }

    // ---- Su girdisi ----

    /** Ozel su miktari: yalnizca 1..[MAX_WATER_ML] tam sayi; aksi null (sessizce 0 yazilmaz). */
    fun parseWaterMl(text: String): Int? {
        val t = text.trim()
        if (!Regex("^\\d{1,4}$").matches(t)) return null
        return t.toInt().takeIf { it in 1..MAX_WATER_ML }
    }

    /** Yalnizca rakam, en fazla [maxDigits] basamak. */
    fun sanitizeInt(input: String, maxDigits: Int): String = input.filter { it in '0'..'9' }.take(maxDigits)
}

/** Cift dokunusa karsi kisa bekleme: [minGapMs] icinde ikinci cagri gecmez. */
class ClickGate(private val minGapMs: Long = 400L) {
    private var last: Long? = null

    fun tryPass(nowMs: Long): Boolean {
        val l = last
        if (l != null && nowMs - l in 0 until minGapMs) return false
        last = nowMs
        return true
    }
}

/** Ogun formu dogrulamasi. */
object MealForm {
    const val MAX_DESC = 80
    const val MAX_PROTEIN = 300.0
    const val MAX_CARBS = 1000.0
    const val MAX_FAT = 500.0
    const val MAX_KCAL = 5000

    enum class Field { DESCRIPTION, PROTEIN, CALORIES, CARBS, FAT }
    enum class Error { EMPTY, TOO_LONG, INVALID }

    data class Input(
        val type: MealType,
        val description: String = "",
        val protein: String = "",
        val calories: String = "",
        val carbs: String = "",
        val fat: String = "",
    )

    sealed interface Result {
        data class Ok(val meal: Meal) : Result
        data class Invalid(val errors: Map<Field, Error>) : Result
    }

    private val decimal = Regex("^(\\d{1,4}(\\.\\d{0,2})?|\\.\\d{1,2})$")

    /** Virgul noktaya cevrilir; negatif, harf, bilimsel yazim ve cok noktali girdi gecersiz (null). */
    fun parseDecimal(text: String): Double? {
        val t = text.trim().replace(',', '.')
        return if (decimal.matches(t)) t.toDouble() else null
    }

    fun validate(input: Input, epochDay: Long, id: Long = 0): Result {
        val errors = linkedMapOf<Field, Error>()
        val desc = input.description.trim()
        if (desc.isEmpty()) errors[Field.DESCRIPTION] = Error.EMPTY
        else if (desc.length > MAX_DESC) errors[Field.DESCRIPTION] = Error.TOO_LONG

        var protein: Double? = null
        if (input.protein.isBlank()) errors[Field.PROTEIN] = Error.EMPTY
        else {
            protein = parseDecimal(input.protein)?.takeIf { it <= MAX_PROTEIN }
            if (protein == null) errors[Field.PROTEIN] = Error.INVALID
        }

        var kcal = 0
        if (input.calories.isNotBlank()) {
            val t = input.calories.trim()
            val v = if (Regex("^\\d{1,5}$").matches(t)) t.toInt() else null
            if (v == null || v > MAX_KCAL) errors[Field.CALORIES] = Error.INVALID else kcal = v
        }

        fun optional(text: String, field: Field, max: Double): Double? {
            if (text.isBlank()) return null
            val v = parseDecimal(text)?.takeIf { it <= max }
            if (v == null) errors[field] = Error.INVALID
            return v
        }
        val carbs = optional(input.carbs, Field.CARBS, MAX_CARBS)
        val fat = optional(input.fat, Field.FAT, MAX_FAT)

        if (errors.isNotEmpty() || protein == null) return Result.Invalid(errors)
        return Result.Ok(Meal(id, epochDay, input.type, desc, protein, kcal, carbs, fat))
    }

    /** Duzenleme formunu mevcut ogunden doldurur. */
    fun inputOf(m: Meal): Input = Input(
        m.mealType, m.description, num(m.proteinG),
        if (m.calories > 0) m.calories.toString() else "",
        m.carbsG?.let(::num).orEmpty(), m.fatG?.let(::num).orEmpty(),
    )

    private fun num(v: Double): String = if (v == Math.floor(v) && v < 1e9) v.toLong().toString() else v.toString()
}

package com.emir.gym.data

import java.time.LocalDate

/**
 * Beslenme hedefleri. bodyWeightKg kullanicidan gelir; yoksa protein araligi gosterilmez.
 */
data class NutritionGoals(
    val bodyWeightKg: Double? = null,
    val proteinPerKgMin: Double = 1.6,
    val proteinPerKgMax: Double = 2.0,
    val waterMl: Int = 2500,
    val calories: Int? = null,
) {
    /** kg x g/kg araligi; kilo yoksa ya da gecersizse null. */
    fun proteinRange(): ClosedFloatingPointRange<Double>? {
        val w = bodyWeightKg ?: return null
        if (w <= 0.0 || proteinPerKgMin <= 0.0 || proteinPerKgMax < proteinPerKgMin) return null
        return (w * proteinPerKgMin)..(w * proteinPerKgMax)
    }
}

data class DailyNutrition(
    val date: LocalDate,
    val proteinG: Double,
    val calories: Int,
    val waterMl: Int,
    val proteinRange: ClosedFloatingPointRange<Double>?,
    /** Alinan protein / araligin alt siniri (1.0 = alt hedefe ulasildi); hedef yoksa null. */
    val proteinRatio: Double?,
    val waterRatio: Double?,
    val calorieRatio: Double?,
)

data class WeekAverage(val weekStart: LocalDate, val avgKg: Double, val count: Int)

object NutritionLogic {
    fun daily(
        date: LocalDate,
        meals: List<Meal>,
        water: List<WaterLog>,
        goals: NutritionGoals,
    ): DailyNutrition {
        val d = date.toEpochDay()
        val m = meals.filter { it.epochDay == d }
        val protein = m.sumOf { it.proteinG }
        val kcal = m.sumOf { it.calories }
        val ml = water.filter { it.epochDay == d }.sumOf { it.ml }
        val range = goals.proteinRange()
        return DailyNutrition(
            date = date, proteinG = protein, calories = kcal, waterMl = ml,
            proteinRange = range,
            proteinRatio = range?.let { protein / it.start },
            waterRatio = if (goals.waterMl > 0) ml.toDouble() / goals.waterMl else null,
            calorieRatio = goals.calories?.takeIf { it > 0 }?.let { kcal.toDouble() / it },
        )
    }

    /** Tarihe gore artan tarti serisi. */
    fun weightSeries(weights: List<BodyWeight>): List<BodyWeight> = weights.sortedBy { it.epochDay }

    /** Pazartesi-Pazar haftalarina gore ortalama; artan hafta sirasi. */
    fun weeklyAverages(weights: List<BodyWeight>): List<WeekAverage> =
        weights.groupBy { WeeklyStats.weekStart(LocalDate.ofEpochDay(it.epochDay)) }
            .toSortedMap()
            .map { (start, list) -> WeekAverage(start, list.sumOf { it.kg } / list.size, list.size) }
}

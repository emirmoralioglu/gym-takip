package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionLogicTest {
    private val day = LocalDate.of(2024, 1, 3) // Carsamba
    private val d = day.toEpochDay()

    private fun meal(epoch: Long, protein: Double, kcal: Int) = Meal(0, epoch, MealType.OGLE, "", protein, kcal)

    @Test fun defaultsHaveNoBodyWeightAndNoProteinRange() {
        val g = NutritionGoals()
        assertNull(g.bodyWeightKg); assertNull(g.calories)
        assertEquals(2500, g.waterMl)
        assertNull(g.proteinRange())
    }

    @Test fun proteinRangeFromWeight() {
        val r = NutritionGoals(bodyWeightKg = 75.0).proteinRange()!!
        assertEquals(120.0, r.start, 1e-9); assertEquals(150.0, r.endInclusive, 1e-9)
        assertNull(NutritionGoals(bodyWeightKg = 0.0).proteinRange())
        assertNull(NutritionGoals(bodyWeightKg = 70.0, proteinPerKgMin = 2.0, proteinPerKgMax = 1.6).proteinRange())
    }

    @Test fun dailySummaryOnlyCountsThatDay() {
        val meals = listOf(meal(d, 30.0, 500), meal(d, 40.5, 700), meal(d - 1, 99.0, 999), meal(d + 1, 99.0, 999))
        val water = listOf(WaterLog(0, d, 500), WaterLog(0, d, 750), WaterLog(0, d - 1, 3000))
        val s = NutritionLogic.daily(day, meals, water, NutritionGoals(bodyWeightKg = 75.0, calories = 2400))
        assertEquals(70.5, s.proteinG, 1e-9); assertEquals(1200, s.calories); assertEquals(1250, s.waterMl)
        assertEquals(70.5 / 120.0, s.proteinRatio!!, 1e-9)
        assertEquals(0.5, s.waterRatio!!, 1e-9)
        assertEquals(0.5, s.calorieRatio!!, 1e-9)
        assertNotNull(s.proteinRange)
    }

    @Test fun emptyDayAndMissingGoals() {
        val s = NutritionLogic.daily(day, emptyList(), emptyList(), NutritionGoals())
        assertEquals(0.0, s.proteinG, 0.0); assertEquals(0, s.calories); assertEquals(0, s.waterMl)
        assertNull(s.proteinRatio); assertNull(s.calorieRatio)
        assertEquals(0.0, s.waterRatio!!, 0.0)
    }

    @Test fun ratioMayExceedOne() {
        val s = NutritionLogic.daily(day, emptyList(), listOf(WaterLog(0, d, 5000)), NutritionGoals())
        assertEquals(2.0, s.waterRatio!!, 1e-9)
    }

    @Test fun weightSeriesSortedAndWeeklyAveragesMondayToSunday() {
        val mon = LocalDate.of(2024, 1, 1)
        val w = listOf(
            BodyWeight(mon.plusDays(7).toEpochDay(), 74.0),     // sonraki Pzt
            BodyWeight(mon.plusDays(6).toEpochDay(), 75.0),     // Pazar -> ilk hafta
            BodyWeight(mon.toEpochDay(), 76.0),                 // Pzt -> ilk hafta
            BodyWeight(mon.minusDays(1).toEpochDay(), 80.0),    // onceki Pazar
        )
        assertEquals(w.map { it.epochDay }.sorted(), NutritionLogic.weightSeries(w).map { it.epochDay })
        val avg = NutritionLogic.weeklyAverages(w)
        assertEquals(listOf(mon.minusDays(7), mon, mon.plusDays(7)), avg.map { it.weekStart })
        assertEquals(80.0, avg[0].avgKg, 1e-9)
        assertEquals(75.5, avg[1].avgKg, 1e-9); assertEquals(2, avg[1].count)
        assertEquals(74.0, avg[2].avgKg, 1e-9)
        assertTrue(NutritionLogic.weeklyAverages(emptyList()).isEmpty())
        assertTrue(NutritionLogic.weightSeries(emptyList()).isEmpty())
    }
}

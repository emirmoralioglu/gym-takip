package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Kilo tek kaynak (protein hedefi) ve hafta baslangici normalizasyonu. */
class WeightSourceTest {
    @Test fun effectiveWeightPrefersLatestWeighIn() {
        val w = listOf(BodyWeight(10, 80.0), BodyWeight(30, 76.0), BodyWeight(20, 78.0))
        assertEquals(76.0, NutritionLogic.effectiveWeightKg(w, 90.0)!!, 0.0)
        assertEquals(90.0, NutritionLogic.effectiveWeightKg(emptyList(), 90.0)!!, 0.0)
        assertNull(NutritionLogic.effectiveWeightKg(emptyList(), null))
        assertNull(NutritionLogic.effectiveWeightKg(emptyList(), 0.0))
        assertEquals(90.0, NutritionLogic.effectiveWeightKg(listOf(BodyWeight(5, 0.0)), 90.0)!!, 0.0)
    }

    @Test fun dailyProteinRangeUsesLatestWeighInOverSettings() {
        val day = LocalDate.ofEpochDay(40)
        val goals = NutritionGoals(bodyWeightKg = 90.0)
        val s = NutritionLogic.daily(day, emptyList(), emptyList(), goals, listOf(BodyWeight(30, 75.0)))
        assertEquals(75.0 * 1.6, s.proteinRange!!.start, 1e-9)
        val fallback = NutritionLogic.daily(day, emptyList(), emptyList(), goals)
        assertEquals(90.0 * 1.6, fallback.proteinRange!!.start, 1e-9)
        assertNull(NutritionLogic.daily(day, emptyList(), emptyList(), NutritionGoals()).proteinRange)
    }

    @Test fun weekProgressNormalizesWeekStartToMonday() {
        val mon = LocalDate.of(2024, 1, 1)
        val days = DefaultPlan.days
        for (offset in 0L..6L) {
            val p = PlanLogic.weekProgress(mon.plusDays(offset), mon, days, emptyList(), emptyList())
            assertEquals(mon, p.weekStart)
            assertEquals(mon, p.days.first().date)
            assertEquals(DayOfWeek.MONDAY, p.days.first().date.dayOfWeek)
            assertEquals(mon.plusDays(6), p.days.last().date)
        }
    }
}

package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DailyWeightTest {
    private val weights = listOf(BodyWeight(10, 80.0), BodyWeight(20, 78.0), BodyWeight(30, 76.0))

    @Test fun pastDayUsesNearestEarlierWeighIn() {
        val goals = NutritionGoals(bodyWeightKg = 90.0)
        fun low(day: Long) = NutritionLogic.daily(LocalDate.ofEpochDay(day), emptyList(), emptyList(), goals, weights).proteinRange?.start
        assertEquals(78.0 * 1.6, low(25)!!, 1e-9)
        assertEquals(80.0 * 1.6, low(10)!!, 1e-9)
        assertEquals(76.0 * 1.6, low(99)!!, 1e-9)
        // Hic onceki tarti yok: ayar.
        assertEquals(90.0 * 1.6, low(5)!!, 1e-9)
    }

    @Test fun noEarlierWeighInAndNoSettingIsNull() {
        val d = NutritionLogic.daily(LocalDate.ofEpochDay(5), emptyList(), emptyList(), NutritionGoals(), weights)
        assertNull(d.proteinRange)
        assertNull(NutritionLogic.effectiveWeightKg(weights, null, 5))
    }
}

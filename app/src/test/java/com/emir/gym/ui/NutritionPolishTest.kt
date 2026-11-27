package com.emir.gym.ui

import com.emir.gym.data.Meal
import com.emir.gym.data.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPolishTest {
    private fun meal(kcal: Int, p: Double = 10.0) = Meal(0, 1, MealType.OGLE, "x", p, kcal)

    @Test fun noCaloriesLoggedMeansNothingToShow() {
        val t = NutritionUi.calorieTotal(listOf(meal(0), meal(0)))
        assertFalse(t.hasAny); assertFalse(t.hasMissing)
        assertEquals(0, t.kcal); assertEquals(2, t.missingMeals)
        assertFalse(NutritionUi.calorieTotal(emptyList()).hasAny)
    }

    @Test fun mixedMealsShowTotalAndMissingNote() {
        val t = NutritionUi.calorieTotal(listOf(meal(400), meal(0), meal(250)))
        assertTrue(t.hasAny); assertTrue(t.hasMissing)
        assertEquals(650, t.kcal); assertEquals(2, t.loggedMeals); assertEquals(1, t.missingMeals)
    }

    @Test fun allLoggedHasNoMissingNote() {
        val t = NutritionUi.calorieTotal(listOf(meal(400), meal(250)))
        assertTrue(t.hasAny); assertFalse(t.hasMissing)
    }

    @Test fun gramsRule() {
        assertEquals("32", NutritionUi.grams(32.0))
        assertEquals("32.5", NutritionUi.grams(32.5))
        assertEquals("41.3", NutritionUi.grams(41.25))
        assertEquals("0", NutritionUi.grams(0.0))
        assertEquals(com.emir.gym.ui.components.UiFormat.grouped(1234) + ".5", NutritionUi.grams(1234.5))
        assertEquals("128", NutritionUi.grams(80 * 1.6))
    }
}

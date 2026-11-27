package com.emir.gym.ui

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.data.BodyWeight
import com.emir.gym.data.Meal
import com.emir.gym.data.MealType
import com.emir.gym.data.NutritionGoals
import com.emir.gym.data.NutritionLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NutritionUiTest {
    private fun meal(id: Long, day: Long, desc: String = "Yulaf", p: Double = 20.0, kcal: Int = 300, type: MealType = MealType.KAHVALTI, c: Double? = null, f: Double? = null) =
        Meal(id, day, type, desc, p, kcal, c, f)

    private fun act(day: Long, t: ActivityType) = ActivityLog(0, day, t, 30)

    // ---- Gun gezinme ----

    @Test fun shiftDayNeverPassesToday() {
        assertEquals(9L, NutritionUi.shiftDay(10, -1, 10))
        assertEquals(10L, NutritionUi.shiftDay(10, 1, 10))
        assertEquals(10L, NutritionUi.shiftDay(9, 1, 10))
        assertEquals(10L, NutritionUi.shiftDay(10, 5, 10))
        assertTrue(NutritionUi.canGoNext(9, 10))
        assertFalse(NutritionUi.canGoNext(10, 10))
        assertEquals(10L, NutritionUi.clampToToday(50, 10))
        assertEquals(3L, NutritionUi.clampToToday(3, 10))
    }

    @Test fun dayLabels() {
        assertEquals(NutritionUi.DayLabel.TODAY, NutritionUi.dayLabel(10, 10))
        assertEquals(NutritionUi.DayLabel.YESTERDAY, NutritionUi.dayLabel(9, 10))
        assertEquals(NutritionUi.DayLabel.DATE, NutritionUi.dayLabel(8, 10))
    }

    @Test fun utcMillisFloorsNegative() {
        assertEquals(1L, NutritionUi.utcMillisToEpochDay(86_400_000L))
        assertEquals(-1L, NutritionUi.utcMillisToEpochDay(-1L))
    }

    // ---- Su hedefi ----

    @Test fun waterGoalBonusOnlyForSwimOrSaunaOnThatDay() {
        assertEquals(3000, NutritionUi.waterGoal(2500, 5, listOf(act(5, ActivityType.YUZME))).totalMl)
        assertEquals(500, NutritionUi.waterGoal(2500, 5, listOf(act(5, ActivityType.SAUNA))).bonusMl)
        assertEquals(2500, NutritionUi.waterGoal(2500, 5, listOf(act(4, ActivityType.YUZME))).totalMl)
        assertEquals(2500, NutritionUi.waterGoal(2500, 5, listOf(act(5, ActivityType.BUHAR))).totalMl)
        assertEquals(2500, NutritionUi.waterGoal(2500, 5, emptyList()).totalMl)
        // Yuzme + sauna ayni gun: tek seferlik +500
        assertEquals(3000, NutritionUi.waterGoal(2500, 5, listOf(act(5, ActivityType.YUZME), act(5, ActivityType.SAUNA))).totalMl)
    }

    // ---- Durum ----

    @Test fun statusKinds() {
        assertEquals(NutritionUi.StatusKind.REMAINING, NutritionUi.status(50.0, 100.0).kind)
        assertEquals(50.0, NutritionUi.status(50.0, 100.0).amount, 1e-9)
        assertEquals(NutritionUi.StatusKind.REACHED, NutritionUi.status(100.0, 100.0).kind)
        assertEquals(NutritionUi.StatusKind.OVER, NutritionUi.status(130.0, 100.0).kind)
        assertEquals(30.0, NutritionUi.status(130.0, 100.0).amount, 1e-9)
        // aralik: alt-ust arasi ulasildi, ustu asim
        assertEquals(NutritionUi.StatusKind.REACHED, NutritionUi.status(140.0, 120.0, 150.0).kind)
        assertEquals(NutritionUi.StatusKind.OVER, NutritionUi.status(155.0, 120.0, 150.0).kind)
        assertEquals(5.0, NutritionUi.status(155.0, 120.0, 150.0).amount, 1e-9)
    }

    // ---- Ogunler ----

    @Test fun groupsInTypeOrderAndSkipsEmpty() {
        val ms = listOf(
            meal(1, 5, type = MealType.AKSAM), meal(2, 5, type = MealType.KAHVALTI),
            meal(3, 5, type = MealType.AKSAM), meal(4, 6, type = MealType.OGLE),
        ).let { NutritionUi.mealsOfDay(it, 5) }
        val g = NutritionUi.groupByType(ms)
        assertEquals(listOf(MealType.KAHVALTI, MealType.AKSAM), g.map { it.first })
        assertEquals(listOf(1L, 3L), g[1].second.map { it.id })
    }

    @Test fun defaultTypeByHour() {
        assertEquals(MealType.KAHVALTI, NutritionUi.defaultMealType(8))
        assertEquals(MealType.OGLE, NutritionUi.defaultMealType(12))
        assertEquals(MealType.ARA, NutritionUi.defaultMealType(16))
        assertEquals(MealType.AKSAM, NutritionUi.defaultMealType(20))
    }

    @Test fun recentMealsAreUniqueNewestFirstAndLimited() {
        val ms = listOf(
            meal(1, 1, "Yulaf", 20.0, 300),
            meal(2, 2, "Tavuk", 40.0, 500, MealType.OGLE),
            meal(3, 3, " yulaf ", 20.0, 300, MealType.ARA), // ayni (bosluk/buyuk-kucuk): en yenisi kalir
            meal(4, 3, "Yulaf", 25.0, 300),                  // makro farkli: ayri
            meal(5, 3, "", 10.0, 100),                       // bos aciklama atlanir
        )
        val r = NutritionUi.recentMeals(ms)
        assertEquals(listOf(4L, 3L, 2L), r.map { it.id })
        assertEquals(MealType.ARA, r[1].mealType)
        val many = (1..20L).map { meal(it, it, "Yemek $it") }
        assertEquals(8, NutritionUi.recentMeals(many).size)
        assertEquals(20L, NutritionUi.recentMeals(many).first().id)
        assertTrue(NutritionUi.recentMeals(emptyList()).isEmpty())
    }

    @Test fun copyToCreatesNewRowOnTargetDay() {
        val c = NutritionUi.copyTo(meal(7, 1, "Yulaf"), 99)
        assertEquals(0L, c.id); assertEquals(99L, c.epochDay); assertEquals("Yulaf", c.description)
    }

    // ---- Haftalik ----

    @Test fun weeklyProteinSevenDaysAndAverageOverLoggedDays() {
        val ms = listOf(meal(1, 10, p = 100.0), meal(2, 10, p = 20.0), meal(3, 8, p = 60.0), meal(4, 2, p = 999.0))
        val w = NutritionUi.weeklyProtein(ms, 10)
        assertEquals(7, w.days.size)
        assertEquals(4L, w.days.first().epochDay); assertEquals(10L, w.days.last().epochDay)
        assertEquals(120.0, w.days.last().proteinG, 1e-9)
        assertEquals(0.0, w.days[3].proteinG, 1e-9) // gun 7
        assertEquals(90.0, w.averageG!!, 1e-9)      // (120 + 60) / 2
        assertTrue(w.hasData)
    }

    @Test fun weeklyProteinEmpty() {
        val w = NutritionUi.weeklyProtein(listOf(meal(1, 100, p = 50.0)), 10)
        assertFalse(w.hasData); assertNull(w.averageG)
    }

    @Test fun loggedDayWithZeroProteinCountsInAverage() {
        val w = NutritionUi.weeklyProtein(listOf(meal(1, 10, p = 0.0), meal(2, 9, p = 100.0)), 10)
        assertEquals(50.0, w.averageG!!, 1e-9)
    }

    // ---- Su girdisi ----

    @Test fun waterParsing() {
        assertEquals(300, NutritionUi.parseWaterMl("300"))
        assertEquals(300, NutritionUi.parseWaterMl(" 300 "))
        assertEquals(5000, NutritionUi.parseWaterMl("5000"))
        assertNull(NutritionUi.parseWaterMl(""))
        assertNull(NutritionUi.parseWaterMl("0"))
        assertNull(NutritionUi.parseWaterMl("5001"))
        assertNull(NutritionUi.parseWaterMl("-5"))
        assertNull(NutritionUi.parseWaterMl("2.5"))
        assertEquals("1234", NutritionUi.sanitizeInt("12a34567", 4))
    }

    // ---- Cift dokunus ----

    @Test fun clickGateBlocksQuickSecondTap() {
        val g = ClickGate(400)
        assertTrue(g.tryPass(1000))
        assertFalse(g.tryPass(1200))
        assertFalse(g.tryPass(1399))
        assertTrue(g.tryPass(1400))
        assertTrue(ClickGate(400).tryPass(0))
    }

    // ---- Form dogrulama ----

    private fun input(desc: String = "Tavuk", p: String = "30", k: String = "", c: String = "", f: String = "") =
        MealForm.Input(MealType.OGLE, desc, p, k, c, f)

    private fun ok(i: MealForm.Input): Meal = (MealForm.validate(i, 5) as MealForm.Result.Ok).meal
    private fun errs(i: MealForm.Input) = (MealForm.validate(i, 5) as MealForm.Result.Invalid).errors

    @Test fun validMealWithOptionalBlanksIsNullNotZero() {
        val m = ok(input())
        assertEquals(30.0, m.proteinG, 0.0); assertEquals(0, m.calories)
        assertNull(m.carbsG); assertNull(m.fatG)
        assertEquals(5L, m.epochDay); assertEquals("Tavuk", m.description)
    }

    @Test fun commaBecomesDot() {
        val m = ok(input(p = "12,5", c = "40,25", f = "3,5"))
        assertEquals(12.5, m.proteinG, 0.0); assertEquals(40.25, m.carbsG!!, 0.0); assertEquals(3.5, m.fatG!!, 0.0)
        assertEquals(12.5, MealForm.parseDecimal("12.5")!!, 0.0)
    }

    @Test fun emptyDescriptionRejected() {
        assertEquals(MealForm.Error.EMPTY, errs(input(desc = "   "))[MealForm.Field.DESCRIPTION])
        assertEquals(MealForm.Error.TOO_LONG, errs(input(desc = "a".repeat(81)))[MealForm.Field.DESCRIPTION])
    }

    @Test fun blankProteinRejectedNotZero() {
        assertEquals(MealForm.Error.EMPTY, errs(input(p = ""))[MealForm.Field.PROTEIN])
        assertEquals(0.0, ok(input(p = "0")).proteinG, 0.0) // acik 0 serbest
    }

    @Test fun negativeHugeAndGarbageRejected() {
        assertEquals(MealForm.Error.INVALID, errs(input(p = "-5"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(p = "301"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(p = "abc"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(p = "1e3"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(p = "NaN"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(p = "1,2,3"))[MealForm.Field.PROTEIN])
        assertEquals(MealForm.Error.INVALID, errs(input(k = "5001"))[MealForm.Field.CALORIES])
        assertEquals(MealForm.Error.INVALID, errs(input(k = "-1"))[MealForm.Field.CALORIES])
        assertEquals(MealForm.Error.INVALID, errs(input(k = "350.5"))[MealForm.Field.CALORIES])
        assertEquals(MealForm.Error.INVALID, errs(input(c = "1001"))[MealForm.Field.CARBS])
        assertEquals(MealForm.Error.INVALID, errs(input(f = "501"))[MealForm.Field.FAT])
        assertEquals(5000, ok(input(k = "5000")).calories)
    }

    @Test fun allErrorsReportedTogetherAndEditKeepsId() {
        val e = errs(input(desc = "", p = "", k = "x"))
        assertEquals(setOf(MealForm.Field.DESCRIPTION, MealForm.Field.PROTEIN, MealForm.Field.CALORIES), e.keys)
        val m = (MealForm.validate(input(), 77, id = 9) as MealForm.Result.Ok).meal
        assertEquals(9L, m.id); assertEquals(77L, m.epochDay)
    }

    @Test fun inputOfRoundTrips() {
        val m = meal(3, 4, "Pilav", 7.5, 250, MealType.AKSAM, c = 50.0, f = 2.5)
        val i = MealForm.inputOf(m)
        assertEquals("7.5", i.protein); assertEquals("250", i.calories); assertEquals("50", i.carbs); assertEquals("2.5", i.fat)
        assertEquals(m.copy(id = 0, epochDay = 4), (MealForm.validate(i, 4) as MealForm.Result.Ok).meal)
        assertEquals("", MealForm.inputOf(meal(1, 1, kcal = 0)).calories)
    }

    // ---- Hedef: gune gore tarti (eski daily cagrilarinin weights bilgisi) ----

    @Test fun proteinTargetUsesWeighInOnOrBeforeThatDay() {
        val weights = listOf(BodyWeight(10, 70.0), BodyWeight(20, 80.0))
        val goals = NutritionGoals()
        fun low(day: Long) = NutritionLogic.daily(LocalDate.ofEpochDay(day), emptyList(), emptyList(), goals, weights).proteinRange?.start
        assertNull(low(5))
        assertEquals(112.0, low(15)!!, 1e-9)
        assertEquals(128.0, low(25)!!, 1e-9)
    }
}

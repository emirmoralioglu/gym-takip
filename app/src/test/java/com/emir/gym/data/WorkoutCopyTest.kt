package com.emir.gym.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCopyTest {
    // 2026-11-26 Persembe
    private val today = LocalDate.of(2026, 11, 26)

    private fun e(id: Long, day: LocalDate, name: String = "Squat", sets: Int = 3, reps: Int = 5, kg: Double = 100.0, note: String = "") =
        WorkoutEntry(id, day.toEpochDay(), name, sets, reps, kg, note, "BACAK")

    @Test fun lastWeekSameDayIsSevenDaysBackSameWeekday() {
        val src = WorkoutCopy.lastWeekSameDay(today)
        assertEquals(LocalDate.of(2026, 11, 19), src)
        assertEquals(today.dayOfWeek, src.dayOfWeek)
    }

    @Test fun lastWeekMappingCrossesMonthAndYear() {
        assertEquals(LocalDate.of(2025, 12, 29), WorkoutCopy.lastWeekSameDay(LocalDate.of(2026, 1, 5)))
    }

    @Test fun copiesValuesWithNewIdsAndTargetDay() {
        val all = listOf(e(5, today.minusWeeks(1), kg = 102.5, note = "iyi"), e(6, today.minusWeeks(1), "Leg press", 4, 12, 180.0))
        val plan = WorkoutCopy.plan(all, today.minusWeeks(1), today) as CopyPlan.Ready
        assertEquals(2, plan.items.size)
        assertTrue(plan.items.all { it.id == 0L && it.epochDay == today.toEpochDay() })
        assertEquals(102.5, plan.items[0].weightKg, 0.0)
        assertEquals("iyi", plan.items[0].note)
        assertEquals("BACAK", plan.items[0].muscleGroup)
        assertEquals(4, plan.items[1].sets)
        assertFalse(plan.needsConfirm)
    }

    @Test fun emptySourceDayReported() {
        val plan = WorkoutCopy.plan(listOf(e(1, today.minusDays(1))), today.minusWeeks(1), today)
        assertEquals(CopyPlan.EmptySource(today.minusWeeks(1)), plan)
    }

    @Test fun sameDayRejected() {
        assertEquals(CopyPlan.SameDay, WorkoutCopy.plan(listOf(e(1, today)), today, today))
    }

    @Test fun secondCopyNeedsConfirmation() {
        val src = today.minusWeeks(1)
        var all = listOf(e(1, src), e(2, src, "Leg press"))
        val first = WorkoutCopy.plan(all, src, today) as CopyPlan.Ready
        all = all + first.items.mapIndexed { i, it -> it.copy(id = 10L + i) }
        val second = WorkoutCopy.plan(all, src, today) as CopyPlan.Ready
        assertEquals(2, second.duplicates)
        assertTrue(second.needsConfirm)
    }

    @Test fun differentValuesOnTargetAreNotDuplicates() {
        val src = today.minusWeeks(1)
        val all = listOf(e(1, src, kg = 100.0), e(2, today, kg = 105.0), e(3, today, "Bench press"))
        val plan = WorkoutCopy.plan(all, src, today) as CopyPlan.Ready
        assertEquals(0, plan.duplicates)
    }

    @Test fun selectableDaysAreOnlyPastDaysWithEntries() {
        val all = listOf(e(1, today.minusDays(3)), e(2, today.minusDays(3)), e(3, today), e(4, today.plusDays(1)))
        assertEquals(setOf(today.minusDays(3).toEpochDay()), WorkoutCopy.daysWithEntriesBefore(all, today))
    }
}

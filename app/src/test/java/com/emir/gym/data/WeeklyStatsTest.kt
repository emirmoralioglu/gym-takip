package com.emir.gym.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyStatsTest {
    private val monday = LocalDate.of(2026, 11, 23) // Pazartesi

    private fun e(day: LocalDate, sets: Int, reps: Int, kg: Double) =
        WorkoutEntry(epochDay = day.toEpochDay(), exercise = "Squat", sets = sets, reps = reps, weightKg = kg)

    @Test fun weekStartIsMonday() {
        assertEquals(monday, WeeklyStats.weekStart(monday))
        assertEquals(monday, WeeklyStats.weekStart(monday.plusDays(6))) // Pazar
        assertEquals(monday.plusDays(7), WeeklyStats.weekStart(monday.plusDays(7)))
    }

    @Test fun emptyWeekIsAllZero() {
        val s = WeeklyStats.summarize(emptyList(), monday)
        assertEquals(0, s.totalSets)
        assertEquals(0.0, s.totalVolume, 0.0)
        assertEquals(0, s.trainingDays)
        assertEquals(List(7) { 0.0 }, s.dailyVolume)
    }

    @Test fun boundariesMondayAndSundayIncludedNeighborsExcluded() {
        val entries = listOf(
            e(monday.minusDays(1), 5, 5, 100.0),  // onceki Pazar: haric
            e(monday, 3, 10, 20.0),               // Pzt: dahil
            e(monday.plusDays(6), 2, 5, 40.0),    // Paz: dahil
            e(monday.plusDays(7), 9, 9, 99.0),    // sonraki Pzt: haric
        )
        val s = WeeklyStats.summarize(entries, monday)
        assertEquals(5, s.totalSets)
        assertEquals(3 * 10 * 20.0 + 2 * 5 * 40.0, s.totalVolume, 0.001)
        assertEquals(2, s.trainingDays)
        assertEquals(600.0, s.dailyVolume[0], 0.001)
        assertEquals(400.0, s.dailyVolume[6], 0.001)
    }

    @Test fun sameDayCountsOneTrainingDay() {
        val entries = listOf(e(monday.plusDays(2), 3, 8, 50.0), e(monday.plusDays(2), 4, 6, 60.0))
        val s = WeeklyStats.summarize(entries, monday)
        assertEquals(1, s.trainingDays)
        assertEquals(7, s.totalSets)
        assertEquals(1200.0 + 1440.0, s.dailyVolume[2], 0.001)
    }
}

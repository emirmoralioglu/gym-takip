package com.emir.gym.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressChartTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun e(daysAgo: Long, name: String, reps: Int, kg: Double, id: Long = daysAgo * 10 + reps) =
        WorkoutEntry(id, today.toEpochDay() - daysAgo, name, 3, reps, kg)

    @Test fun groupsByDayTakingMaxAndSortsAscending() {
        val list = listOf(
            e(2, "Squat", 5, 100.0), e(2, "Squat", 10, 80.0), // ayni gun: max kg 100, 1RM max(116.7, 106.7)
            e(9, "Squat", 5, 90.0),
            e(1, "Bench press", 5, 60.0),
        )
        val p = ProgressChart.points(list, "squat", ChartRange.ALL, today)
        assertEquals(listOf(today.toEpochDay() - 9, today.toEpochDay() - 2), p.map { it.epochDay })
        assertEquals(100.0, p[1].maxKg, 0.0)
        assertEquals(100.0 * (1 + 5 / 30.0), p[1].oneRm!!, 1e-9)
    }

    @Test fun highRepRowsAreSkippedFor1RmLine() {
        val list = listOf(e(2, "Squat", 12, 20.0), e(2, "Squat", 100, 30.0), e(1, "Squat", 13, 20.0))
        val p = ProgressChart.points(list, "Squat", ChartRange.ALL, today)
        assertEquals(30.0, p[0].maxKg, 0.0)
        assertEquals(20.0 * 1.4, p[0].oneRm!!, 1e-9) // 100 tekrarlik satir 1RM'e girmez
        assertEquals(null, p[1].oneRm) // yalniz 13 tekrar: 1RM yok
    }

    @Test fun rangeBoundaries() {
        val list = listOf(e(27, "Squat", 5, 90.0), e(28, "Squat", 5, 91.0), e(83, "Squat", 5, 80.0), e(84, "Squat", 5, 79.0))
        // 4 hafta = bugun dahil son 28 gun: 0..27 gun once
        assertEquals(1, ProgressChart.points(list, "Squat", ChartRange.WEEKS_4, today).size)
        assertEquals(3, ProgressChart.points(list, "Squat", ChartRange.WEEKS_12, today).size)
        assertEquals(4, ProgressChart.points(list, "Squat", ChartRange.ALL, today).size)
    }

    @Test fun emptyWhenNoMatchOrOnlyZeroWeight() {
        val list = listOf(e(1, "Şınav", 20, 0.0))
        assertTrue(ProgressChart.points(list, "Şınav", ChartRange.ALL, today).isEmpty())
        assertTrue(ProgressChart.points(list, "Squat", ChartRange.ALL, today).isEmpty())
        assertTrue(ProgressChart.points(emptyList(), "Squat", ChartRange.ALL, today).isEmpty())
    }

    @Test fun exerciseNamesOrderedByFrequency() {
        val list = listOf(e(1, "Bench press", 5, 60.0), e(2, "Squat", 5, 90.0), e(3, "Squat ", 5, 95.0), e(4, "Şınav", 5, 0.0))
        assertEquals(listOf("Squat", "Bench press"), ProgressChart.exerciseNames(list))
    }
}

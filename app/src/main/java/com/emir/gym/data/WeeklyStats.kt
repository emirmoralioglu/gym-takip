package com.emir.gym.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekSummary(
    val weekStart: LocalDate,
    val totalSets: Int,
    val totalVolume: Double,
    val trainingDays: Int,
    /** Pazartesi=0 ... Pazar=6 icin gunluk hacim (kg). */
    val dailyVolume: List<Double>,
)

object WeeklyStats {
    /** Haftanin Pazartesi gunu. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun volume(e: WorkoutEntry): Double = e.sets.toDouble() * e.reps * e.weightKg

    /** weekStart'tan baslayan 7 gunluk (Pzt-Paz, sinirlar dahil) ozet. */
    fun summarize(entries: List<WorkoutEntry>, weekStart: LocalDate): WeekSummary {
        val start = weekStart.toEpochDay()
        val inWeek = entries.filter { it.epochDay in start..(start + 6) }
        val daily = DoubleArray(7)
        inWeek.forEach { daily[(it.epochDay - start).toInt()] += volume(it) }
        return WeekSummary(
            weekStart = weekStart,
            totalSets = inWeek.sumOf { it.sets },
            totalVolume = daily.sum(),
            trainingDays = inWeek.map { it.epochDay }.distinct().size,
            dailyVolume = daily.toList(),
        )
    }
}

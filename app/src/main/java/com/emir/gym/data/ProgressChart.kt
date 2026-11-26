package com.emir.gym.data

import java.time.LocalDate

enum class ChartRange(val label: String, val weeks: Int?) {
    WEEKS_4("4 hafta", 4),
    WEEKS_12("12 hafta", 12),
    ALL("Tümü", null),
}

/** Bir gun icin en agir kg ve en yuksek tahmini 1RM (12+ tekrar satirlari atlanir; hic yoksa null). */
data class ChartPoint(val epochDay: Long, val maxKg: Double, val oneRm: Double?)

object ProgressChart {
    /** Aralik basi: bugun dahil son [weeks]*7 gun. null = sinirsiz. */
    fun rangeStart(range: ChartRange, today: LocalDate): Long? =
        range.weeks?.let { today.toEpochDay() - it * 7L + 1 }

    fun points(
        entries: List<WorkoutEntry>,
        exercise: String,
        range: ChartRange,
        today: LocalDate,
    ): List<ChartPoint> {
        val k = ExerciseCatalog.key(exercise)
        val from = rangeStart(range, today)
        return entries
            .filter { ExerciseCatalog.key(it.exercise) == k && it.weightKg > 0 }
            .filter { from == null || it.epochDay >= from }
            .groupBy { it.epochDay }
            .map { (day, list) -> ChartPoint(day, list.maxOf { it.weightKg }, list.mapNotNull { Records.oneRm(it) }.maxOrNull()) }
            .sortedBy { it.epochDay }
    }

    /** Agirlikli kaydi olan egzersiz adlari (en cok kayit olan basta). */
    fun exerciseNames(entries: List<WorkoutEntry>): List<String> =
        entries.filter { it.weightKg > 0 }
            .groupBy { ExerciseCatalog.key(it.exercise) }
            .values
            .sortedWith(compareByDescending<List<WorkoutEntry>> { it.size }.thenBy { ExerciseCatalog.key(it.first().exercise) })
            .map { ExerciseCatalog.displayName(it.first().exercise) }
}

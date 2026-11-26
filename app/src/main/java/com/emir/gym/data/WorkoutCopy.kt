package com.emir.gym.data

import java.time.LocalDate

sealed interface CopyPlan {
    /** Kaynak gunde kayit yok. */
    data class EmptySource(val source: LocalDate) : CopyPlan

    /** Kaynak ve hedef ayni gun. */
    data object SameDay : CopyPlan

    /** [duplicates] hedef gunde zaten ayni egzersiz/set/tekrar/kg olan kayit sayisi; >0 ise onay gerekir. */
    data class Ready(val items: List<WorkoutEntry>, val duplicates: Int) : CopyPlan {
        val needsConfirm: Boolean get() = duplicates > 0
    }
}

object WorkoutCopy {
    /** Hedef gunun bir hafta oncesi (ayni haftanin gunu). */
    fun lastWeekSameDay(target: LocalDate): LocalDate = target.minusWeeks(1)

    private fun sig(e: WorkoutEntry) = listOf(ExerciseCatalog.key(e.exercise), e.sets, e.reps, e.weightKg)

    fun plan(all: List<WorkoutEntry>, source: LocalDate, target: LocalDate): CopyPlan {
        if (source == target) return CopyPlan.SameDay
        val src = all.filter { it.epochDay == source.toEpochDay() }.sortedBy { it.id }
        if (src.isEmpty()) return CopyPlan.EmptySource(source)
        val existing = all.filter { it.epochDay == target.toEpochDay() }.map(::sig).toSet()
        val items = src.map { it.copy(id = 0, epochDay = target.toEpochDay()) }
        return CopyPlan.Ready(items, items.count { sig(it) in existing })
    }

    /** Kopyalanabilecek (kayitli ve hedeften once) gunler; secici icin. */
    fun daysWithEntriesBefore(all: List<WorkoutEntry>, target: LocalDate): Set<Long> =
        all.map { it.epochDay }.filter { it < target.toEpochDay() }.toSet()
}

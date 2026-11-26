package com.emir.gym.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class DayStatus { TAMAMLANDI, PLANLI, DINLENME, KACIRILDI }

data class DayPlan(val day: PlanDay, val exercises: List<PlanExercise>)

data class DayProgress(val date: LocalDate, val day: PlanDay?, val status: DayStatus)

data class WeekProgress(
    val weekStart: LocalDate,
    /** Pazartesi'den Pazar'a 7 gun. */
    val days: List<DayProgress>,
    val plannedWeightDays: Int,
    val doneWeightDays: Int,
    val plannedSwimDays: Int,
    val doneSwimDays: Int,
) {
    /** "x / 4" gosterimi icin. */
    val weightLabel: String get() = "$doneWeightDays / $plannedWeightDays"
}

data class ExerciseProgress(val plannedSets: Int, val doneSets: Int) {
    val complete: Boolean get() = plannedSets > 0 && doneSets >= plannedSets
}

data class Suggestion(val sets: Int, val reps: Int, val weightKg: Double?, val fromHistory: Boolean)

data class DayLog(val date: LocalDate, val entries: List<WorkoutEntry>)

object PlanLogic {
    const val DEFAULT_SETS = 3
    const val DEFAULT_REPS = 8

    /** Bir hafta gununun (1..7) plani; plan_days'te yoksa null. Hareketler position sirasinda. */
    fun planOf(weekday: Int, days: List<PlanDay>, exercises: List<PlanExercise>): DayPlan? {
        val day = days.firstOrNull { it.weekday == weekday } ?: return null
        return DayPlan(day, exercises.filter { it.weekday == weekday }.sortedWith(compareBy({ it.position }, { it.id })))
    }

    fun planFor(date: LocalDate, days: List<PlanDay>, exercises: List<PlanExercise>): DayPlan? =
        planOf(date.dayOfWeek.value, days, exercises)

    /**
     * Haftalik ilerleme. Verilen tarih haftanin herhangi bir gunu olabilir; o haftanin Pazartesi'sine
     * normalize edilir (sonuctaki weekStart Pazartesi'dir). today gecmis/gelecek ayrimi icin.
     */
    fun weekProgress(
        weekStartInput: LocalDate,
        today: LocalDate,
        days: List<PlanDay>,
        entries: List<WorkoutEntry>,
        activities: List<ActivityLog>,
    ): WeekProgress {
        val weekStart = weekStartInput.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lifted = entries.map { it.epochDay }.toSet()
        val swum = activities.filter { it.type == ActivityType.YUZME }.map { it.epochDay }.toSet()
        val list = (0..6).map { i ->
            val date = weekStart.plusDays(i.toLong())
            val day = days.firstOrNull { it.weekday == date.dayOfWeek.value }
            val epoch = date.toEpochDay()
            val status = when (day?.type) {
                null, PlanDayType.DINLENME -> DayStatus.DINLENME
                PlanDayType.AGIRLIK -> statusOf(epoch in lifted, date, today)
                PlanDayType.YUZME -> statusOf(epoch in swum, date, today)
            }
            DayProgress(date, day, status)
        }
        fun count(type: PlanDayType) = list.count { it.day?.type == type }
        fun done(type: PlanDayType) = list.count { it.day?.type == type && it.status == DayStatus.TAMAMLANDI }
        return WeekProgress(
            weekStart, list,
            plannedWeightDays = count(PlanDayType.AGIRLIK), doneWeightDays = done(PlanDayType.AGIRLIK),
            plannedSwimDays = count(PlanDayType.YUZME), doneSwimDays = done(PlanDayType.YUZME),
        )
    }

    private fun statusOf(hasRecord: Boolean, date: LocalDate, today: LocalDate) = when {
        hasRecord -> DayStatus.TAMAMLANDI
        date.isBefore(today) -> DayStatus.KACIRILDI
        else -> DayStatus.PLANLI
    }

    /** Planlanan set sayisi ile o tarihte (ayni ad, harf katlamali) yapilan set sayisi. */
    fun exerciseProgress(planned: PlanExercise, date: LocalDate, entries: List<WorkoutEntry>): ExerciseProgress {
        val k = ExerciseCatalog.key(planned.name)
        val done = entries
            .filter { it.epochDay == date.toEpochDay() && ExerciseCatalog.key(it.exercise) == k }
            .sumOf { it.sets }
        return ExerciseProgress(planned.sets, done)
    }

    /** Aynı egzersiz için en son kayıt (tarih, sonra id). */
    fun lastEntry(name: String, entries: List<WorkoutEntry>): WorkoutEntry? {
        val k = ExerciseCatalog.key(name)
        return entries.filter { ExerciseCatalog.key(it.exercise) == k }
            .maxWithOrNull(compareBy({ it.epochDay }, { it.id }))
    }

    /**
     * Form onerisi: gecmis varsa son agirlik ve tekrar, yoksa agirlik bos.
     * Set sayisi plan varsa plandan, yoksa gecmisten, yoksa 3; tekrar gecmis yoksa plan hedefi ya da 8.
     */
    fun suggest(name: String, entries: List<WorkoutEntry>, plan: PlanExercise? = null): Suggestion {
        val last = lastEntry(name, entries)
        return Suggestion(
            sets = plan?.sets ?: last?.sets ?: DEFAULT_SETS,
            reps = last?.reps ?: plan?.targetReps ?: DEFAULT_REPS,
            weightKg = last?.weightKg,
            fromHistory = last != null,
        )
    }

    /** Bir hafta gunune (1..7) dusen tarihlerin kayitlari; en yeni tarih basta. */
    fun weekdayHistory(weekday: Int, entries: List<WorkoutEntry>): List<DayLog> =
        entries.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value == weekday }
            .groupBy { it.epochDay }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) -> DayLog(LocalDate.ofEpochDay(day), list.sortedBy { it.id }) }

    /** Yeni sira: from indeksindeki ogeyi to indeksine tasir (sinir disi ise degismez). */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }
}

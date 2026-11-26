package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanLogicTest {
    private val mon = LocalDate.of(2024, 1, 1) // Pazartesi
    private val days = DefaultPlan.days
    private val ex = DefaultPlan.exercises

    private fun entry(date: LocalDate, name: String = "Leg press", sets: Int = 3, reps: Int = 8, kg: Double = 100.0, id: Long = 0) =
        WorkoutEntry(id, date.toEpochDay(), name, sets, reps, kg)

    private fun swim(date: LocalDate) = ActivityLog(0, date.toEpochDay(), ActivityType.YUZME, 45)

    private fun progress(today: LocalDate, entries: List<WorkoutEntry> = emptyList(), acts: List<ActivityLog> = emptyList()) =
        PlanLogic.weekProgress(mon, today, days, entries, acts)

    // --- varsayilan plan ---
    @Test fun defaultPlanShape() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), days.map { it.weekday })
        val counts = (1..7).map { d -> ex.count { it.weekday == d } }
        assertEquals(listOf(5, 4, 0, 4, 4, 0, 0), counts)
        assertEquals(listOf(PlanDayType.AGIRLIK, PlanDayType.AGIRLIK, PlanDayType.YUZME, PlanDayType.AGIRLIK,
            PlanDayType.AGIRLIK, PlanDayType.YUZME, PlanDayType.DINLENME), days.map { it.type })
        assertTrue(ex.all { it.sets == 3 && it.targetReps == 8 && it.restSeconds == 90 })
        assertEquals(setOf("Reverse pec deck", "Face pull"), ex.filter { it.posture }.map { it.name }.toSet())
    }

    @Test fun everyPlanExerciseResolvesInCatalogWithSameGroup() {
        ex.forEach {
            assertEquals(it.name, MuscleGroup.fromKey(it.muscleGroup), ExerciseCatalog.groupOfName(it.name, emptyList()))
        }
        // Konumlar her gun 0..n-1
        (1..7).forEach { d -> assertEquals((0 until ex.count { it.weekday == d }).toList(), ex.filter { it.weekday == d }.map { it.position }) }
    }

    // --- (a) gunun plani ---
    @Test fun planOfDayAndDate() {
        val p = PlanLogic.planOf(1, days, ex)!!
        assertEquals("Upper A", p.day.title)
        assertEquals(5, p.exercises.size)
        assertEquals(p, PlanLogic.planFor(mon.plusDays(7), days, ex)) // sonraki Pazartesi
        assertEquals(PlanDayType.DINLENME, PlanLogic.planFor(mon.plusDays(6), days, ex)!!.day.type)
        assertNull(PlanLogic.planOf(1, emptyList(), emptyList()))
    }

    @Test fun planExercisesSortedByPosition() {
        val shuffled = ex.filter { it.weekday == 1 }.reversed()
        assertEquals((0..4).toList(), PlanLogic.planOf(1, days, shuffled)!!.exercises.map { it.position })
    }

    // --- (b) haftalik ilerleme ---
    @Test fun emptyWeekMidweek() {
        val p = progress(today = mon.plusDays(2)) // Carsamba
        assertEquals(4, p.plannedWeightDays); assertEquals(0, p.doneWeightDays)
        assertEquals(2, p.plannedSwimDays); assertEquals(0, p.doneSwimDays)
        assertEquals("0 / 4", p.weightLabel)
        assertEquals(
            listOf(DayStatus.KACIRILDI, DayStatus.KACIRILDI, DayStatus.PLANLI, DayStatus.PLANLI,
                DayStatus.PLANLI, DayStatus.PLANLI, DayStatus.DINLENME),
            p.days.map { it.status },
        )
    }

    @Test fun todayWithoutRecordIsPlannedNotMissed() {
        assertEquals(DayStatus.PLANLI, progress(today = mon).days[0].status)
    }

    @Test fun completedDaysCountAndStatus() {
        val entries = listOf(entry(mon, "Lat pulldown"), entry(mon, "Lat pulldown"), entry(mon.plusDays(3)))
        val p = progress(today = mon.plusDays(4), entries = entries, acts = listOf(swim(mon.plusDays(2))))
        assertEquals(2, p.doneWeightDays) // ayni gun cift sayilmaz
        assertEquals("2 / 4", p.weightLabel)
        assertEquals(1, p.doneSwimDays)
        assertEquals(DayStatus.TAMAMLANDI, p.days[0].status)
        assertEquals(DayStatus.KACIRILDI, p.days[1].status)
        assertEquals(DayStatus.TAMAMLANDI, p.days[2].status)
        assertEquals(DayStatus.TAMAMLANDI, p.days[3].status)
        assertEquals(DayStatus.PLANLI, p.days[4].status) // bugun, kayit yok
        assertEquals(DayStatus.PLANLI, p.days[5].status) // gelecek yuzme
    }

    @Test fun futureRecordStillCountsAndRestDayIgnoresRecords() {
        val p = progress(today = mon, entries = listOf(entry(mon.plusDays(4)), entry(mon.plusDays(6))))
        assertEquals(DayStatus.TAMAMLANDI, p.days[4].status)
        assertEquals(DayStatus.DINLENME, p.days[6].status)
        assertEquals(1, p.doneWeightDays)
    }

    @Test fun weekBoundariesAreMondayToSunday() {
        // Onceki Pazar ve sonraki Pazartesi bu haftaya sayilmaz.
        val entries = listOf(entry(mon.minusDays(1)), entry(mon.plusDays(7)))
        val p = progress(today = mon.plusDays(6), entries = entries)
        assertEquals(0, p.doneWeightDays)
        assertEquals(mon, p.days.first().date); assertEquals(mon.plusDays(6), p.days.last().date)
        // Pazar'daki kayit Pazar'in (dinlenme) durumunu degistirmez, Pzt-Cmt gecmis ve kayitsiz.
        assertEquals(DayStatus.KACIRILDI, p.days[0].status)
        assertEquals(DayStatus.KACIRILDI, p.days[5].status)
    }

    @Test fun swimmingOnlyCountsYuzmeActivity() {
        val sauna = ActivityLog(0, mon.plusDays(2).toEpochDay(), ActivityType.SAUNA, 10)
        val p = progress(today = mon.plusDays(6), acts = listOf(sauna))
        assertEquals(0, p.doneSwimDays)
        assertEquals(DayStatus.KACIRILDI, p.days[2].status)
    }

    @Test fun missingPlanDaysTreatedAsRest() {
        val p = PlanLogic.weekProgress(mon, mon, emptyList(), listOf(entry(mon)), emptyList())
        assertEquals(0, p.plannedWeightDays)
        assertTrue(p.days.all { it.status == DayStatus.DINLENME })
    }

    // --- (c) egzersiz ilerlemesi ---
    @Test fun exerciseProgressSumsSetsSameDayWithFolding() {
        val planned = ex.first { it.name == "Leg press" }
        val tue = mon.plusDays(1)
        val entries = listOf(
            entry(tue, "LEG PRESS", sets = 2), entry(tue, " leg press", sets = 1),
            entry(tue, "Leg curl", sets = 3), entry(mon, "Leg press", sets = 3),
        )
        val pr = PlanLogic.exerciseProgress(planned, tue, entries)
        assertEquals(3, pr.plannedSets); assertEquals(3, pr.doneSets); assertTrue(pr.complete)
        val partial = PlanLogic.exerciseProgress(planned, tue, entries.take(1))
        assertEquals(2, partial.doneSets); assertFalse(partial.complete)
        assertEquals(0, PlanLogic.exerciseProgress(planned, tue, emptyList()).doneSets)
    }

    @Test fun exerciseProgressTurkishCaseFolding() {
        val planned = PlanExercise(weekday = 1, position = 0, name = "Incline press", muscleGroup = "GOGUS")
        val e = entry(mon, "INCLINE PRESS", sets = 3)
        val e2 = entry(mon, "İncline press".replace("İ", "I"), sets = 0)
        assertEquals(3, PlanLogic.exerciseProgress(planned, mon, listOf(e, e2)).doneSets)
        val tr = PlanExercise(weekday = 1, position = 0, name = "IŞIK press", muscleGroup = "GOGUS")
        assertEquals(3, PlanLogic.exerciseProgress(tr, mon, listOf(entry(mon, "ışık press", sets = 3))).doneSets)
    }

    // --- (d) oneri ---
    @Test fun suggestionEmptyUsesDefaults() {
        val s = PlanLogic.suggest("Leg press", emptyList())
        assertEquals(Suggestion(3, 8, null, false), s)
    }

    @Test fun suggestionFromPlanWhenNoHistory() {
        val plan = PlanExercise(weekday = 1, position = 0, name = "X", muscleGroup = "SIRT", sets = 4, targetReps = 10)
        assertEquals(Suggestion(4, 10, null, false), PlanLogic.suggest("x", emptyList(), plan))
    }

    @Test fun suggestionUsesLatestEntryByDateThenId() {
        val entries = listOf(
            entry(mon, "Leg press", reps = 10, kg = 90.0, id = 1),
            entry(mon.plusDays(8), "LEG PRESS", reps = 6, kg = 120.0, id = 2),
            entry(mon.plusDays(8), "leg press", reps = 7, kg = 125.0, id = 3),
            entry(mon.plusDays(20), "Leg curl", reps = 12, kg = 40.0, id = 4),
        )
        val s = PlanLogic.suggest("Leg press", entries)
        assertEquals(7, s.reps); assertEquals(125.0, s.weightKg!!, 0.0); assertTrue(s.fromHistory)
        val withPlan = PlanLogic.suggest("Leg press", entries, ex.first { it.name == "Leg press" })
        assertEquals(3, withPlan.sets); assertEquals(7, withPlan.reps); assertEquals(125.0, withPlan.weightKg!!, 0.0)
    }

    // --- (e) hafta gunu gecmisi ---
    @Test fun weekdayHistoryGroupsByDateNewestFirst() {
        val m1 = mon; val m2 = mon.plusDays(7)
        val entries = listOf(
            entry(m1, "A", id = 1), entry(m2, "B", id = 5), entry(m2, "C", id = 3),
            entry(mon.plusDays(1), "Sali", id = 9),
        )
        val h = PlanLogic.weekdayHistory(1, entries)
        assertEquals(listOf(m2, m1), h.map { it.date })
        assertEquals(listOf("C", "B"), h[0].entries.map { it.exercise })
        assertEquals(1, PlanLogic.weekdayHistory(2, entries).size)
        assertTrue(PlanLogic.weekdayHistory(7, entries).isEmpty())
        assertTrue(PlanLogic.weekdayHistory(1, emptyList()).isEmpty())
    }

    // --- siralama ---
    @Test fun moveReorders() {
        assertEquals(listOf(2, 3, 1), PlanLogic.move(listOf(1, 2, 3), 0, 2))
        assertEquals(listOf(3, 1, 2), PlanLogic.move(listOf(1, 2, 3), 2, 0))
        assertEquals(listOf(1, 2, 3), PlanLogic.move(listOf(1, 2, 3), 0, 5))
        assertEquals(emptyList<Int>(), PlanLogic.move(emptyList<Int>(), 0, 0))
    }
}

package com.emir.gym.ui

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.data.FormResult
import com.emir.gym.data.PlanDay
import com.emir.gym.data.PlanDayType
import com.emir.gym.data.PlanExercise
import com.emir.gym.data.WorkoutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FolderLogicTest {
    private val mon = LocalDate.of(2024, 1, 1) // Pazartesi
    private val bench = PlanExercise(id = 7, weekday = 1, position = 0, name = "Bench press", muscleGroup = "GOGUS")
    private val row = PlanExercise(id = 8, weekday = 1, position = 1, name = "Seated cable row", muscleGroup = "SIRT")

    private fun e(id: Long, date: LocalDate, name: String = "Bench press", sets: Int = 1, reps: Int = 8, kg: Double = 60.0) =
        WorkoutEntry(id, date.toEpochDay(), name, sets, reps, kg)

    private fun key(i: Int, p: PlanExercise = bench, d: LocalDate = mon) = RowKey(p.id, d.toEpochDay(), i)

    // --- en yakin hafta gunu tarihi ---
    @Test fun nearestDateIsTodayWhenSameWeekday() {
        assertEquals(mon, FolderLogic.nearestDate(1, mon))
    }

    @Test fun nearestDateGoesBackwardNeverForward() {
        // Carsamba bugun: Pazartesi -> 2 gun once; Persembe -> gecen hafta.
        val wed = mon.plusDays(2)
        assertEquals(mon, FolderLogic.nearestDate(1, wed))
        assertEquals(wed, FolderLogic.nearestDate(3, wed))
        assertEquals(mon.minusDays(4), FolderLogic.nearestDate(4, wed))
        assertEquals(mon.minusDays(1), FolderLogic.nearestDate(7, wed))
    }

    @Test fun selectableOnlySameWeekdayAndNotFuture() {
        val today = mon.plusDays(2)
        assertTrue(FolderLogic.isSelectable(mon, 1, today))
        assertTrue(FolderLogic.isSelectable(mon.minusWeeks(3), 1, today))
        assertFalse(FolderLogic.isSelectable(mon.plusDays(1), 1, today))
        assertFalse(FolderLogic.isSelectable(mon.plusWeeks(1), 1, today))
    }

    // --- sure tahmini ---
    @Test fun estimateMinutesWeightRoundsUpToFive() {
        val day = PlanDay(1, "Upper A", PlanDayType.AGIRLIK)
        val five = (0 until 5).map { bench.copy(id = it.toLong()) } // 5*3*(90+40)=1950 sn = 32.5 dk
        assertEquals(35, FolderLogic.estimateMinutes(day, five))
        assertNull(FolderLogic.estimateMinutes(day, emptyList()))
    }

    @Test fun estimateMinutesSwimReadsNoteOrNull() {
        assertEquals(45, FolderLogic.estimateMinutes(PlanDay(3, "Yuzme", PlanDayType.YUZME, "~45 dk; sauna"), emptyList()))
        assertNull(FolderLogic.estimateMinutes(PlanDay(3, "Yuzme", PlanDayType.YUZME, "serbest"), emptyList()))
        assertNull(FolderLogic.estimateMinutes(PlanDay(7, "Dinlenme", PlanDayType.DINLENME, "~30 dk"), emptyList()))
        assertNull(FolderLogic.estimateMinutes(null, emptyList()))
    }

    @Test fun kgTextDotDecimalNoGrouping() {
        assertEquals("60", FolderLogic.kgText(60.0))
        assertEquals("62.5", FolderLogic.kgText(62.5))
        assertEquals("1000", FolderLogic.kgText(1000.0))
        assertEquals("2.25", FolderLogic.kgText(2.25))
    }

    // --- set satirlari <-> kayit eslemesi ---
    @Test fun reconcileBuildsPlannedRowsWithTargetReps() {
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), emptyList())
        assertEquals(listOf(key(0), key(1), key(2)), rows.keys.toList())
        assertTrue(rows.values.all { it.kg == "" && it.reps == "8" && !it.done })
    }

    @Test fun reconcileRebuildsDoneRowsFromExistingEntries() {
        val entries = listOf(e(11, mon, kg = 62.5, reps = 6), e(12, mon, kg = 60.0), e(13, mon.minusWeeks(1), kg = 99.0))
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), entries)
        assertEquals(RowState("62.5", "6", 11), rows[key(0)])
        assertEquals(RowState("60", "8", 12), rows[key(1)])
        assertEquals(RowState("", "8", null), rows[key(2)])
        assertEquals(3, rows.size)
    }

    @Test fun reconcileMatchesExerciseNamesIgnoringCaseAndSpace() {
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), listOf(e(11, mon, name = "  BENCH PRESS ")))
        assertEquals(11L, rows[key(0)]?.entryId)
    }

    @Test fun reconcileOtherExerciseEntriesDoNotLeak() {
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench, row), mon.toEpochDay(), listOf(e(11, mon, name = "Seated cable row")))
        assertTrue(rows.filterKeys { it.planId == bench.id }.values.none { it.done })
        assertEquals(11L, rows[key(0, row)]?.entryId)
    }

    @Test fun reconcileExtraEntriesBeyondPlanAddRows() {
        val entries = (1L..4L).map { e(it, mon) }
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), entries)
        assertEquals(4, rows.size)
        assertEquals(listOf<Long?>(1, 2, 3, 4), rows.values.map { it.entryId })
    }

    @Test fun reconcileBulkEntryReducesRowsAndCountsNoRow() {
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), listOf(e(5, mon, sets = 2)))
        assertEquals(1, rows.size)
        assertFalse(rows.values.first().done)
    }

    @Test fun reconcileKeepsDoneRowWhoseEntryNotInListYet() {
        val cur = mapOf(key(0) to RowState("60", "8", 99))
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), emptyList())
        assertEquals(99L, rows[key(0)]?.entryId)
    }

    @Test fun reconcileKeepsTypedTextWhenItMatchesEntry() {
        val cur = mapOf(key(0) to RowState("62.", "8", 11))
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(e(11, mon, kg = 62.0)))
        assertEquals("62.", rows[key(0)]?.kg)
    }

    @Test fun reconcileTakesTextFromEntryWhenEditedElsewhere() {
        val cur = mapOf(key(0) to RowState("60", "8", 11))
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(e(11, mon, kg = 65.0, reps = 5)))
        assertEquals(RowState("65", "5", 11), rows[key(0)])
    }

    @Test fun reconcileDoesNotReclaimDeletedEntry() {
        val rows = FolderLogic.reconcile(emptyMap(), listOf(bench), mon.toEpochDay(), listOf(e(11, mon)), ignoreIds = setOf(11))
        assertTrue(rows.values.none { it.done })
    }

    @Test fun reconcileKeepsUndoneRowTextAndExtraRows() {
        val cur = mapOf(
            key(0) to RowState("70", "10", null),
            key(1) to RowState("", "8", null), key(2) to RowState("", "8", null), key(3) to RowState("5", "8", null),
        )
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), emptyList())
        assertEquals(4, rows.size)
        assertEquals("70", rows[key(0)]?.kg)
    }

    @Test fun reconcileDropsRowsOfOtherDates() {
        val other = RowKey(bench.id, mon.minusWeeks(1).toEpochDay(), 0)
        val rows = FolderLogic.reconcile(mapOf(other to RowState("1", "1", 3)), listOf(bench), mon.toEpochDay(), emptyList())
        assertFalse(other in rows)
    }

    @Test fun reconcileUnbindsRowWhenEntryBecameBulk() {
        val cur = mapOf(key(0) to RowState("60", "8", 11))
        val bulk = e(11, mon, sets = 3)
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(bulk))
        assertTrue(rows.values.none { it.done })
        assertEquals(RowState("", "8", null), rows[key(0)])
    }

    @Test fun reconcileUnbindsWhenEntryMovedToOtherDateOrExercise() {
        val cur = mapOf(key(0) to RowState("60", "8", 11))
        val moved = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(e(11, mon.minusWeeks(1))))
        assertFalse(moved[key(0)]!!.done)
        val renamed = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(e(11, mon, name = "Lat pulldown")))
        assertFalse(renamed[key(0)]!!.done)
    }

    @Test fun reconcileNewEntryNeverOverwritesBoundRow() {
        // Satir 0 id 11'e bagli; 12 numarali kayit bagsiz: satir 1'e gitmeli, satir 0 bozulmamali.
        val cur = mapOf(key(0) to RowState("60", "8", 11))
        val rows = FolderLogic.reconcile(cur, listOf(bench), mon.toEpochDay(), listOf(e(11, mon), e(12, mon, kg = 70.0)))
        assertEquals(11L, rows[key(0)]?.entryId)
        assertEquals(12L, rows[key(1)]?.entryId)
    }

    // --- bagli kayit korumasi ---
    @Test fun isBoundToRequiresSingleSetSameDayAndExercise() {
        assertTrue(FolderLogic.isBoundTo(e(1, mon), mon.toEpochDay(), bench))
        assertFalse(FolderLogic.isBoundTo(e(1, mon, sets = 3), mon.toEpochDay(), bench))
        assertFalse(FolderLogic.isBoundTo(e(1, mon.plusDays(1)), mon.toEpochDay(), bench))
        assertFalse(FolderLogic.isBoundTo(e(1, mon, name = "Squat"), mon.toEpochDay(), bench))
    }

    @Test fun editedEntryBlankKgNeverWritesZero() {
        val r = FolderLogic.editedEntry(e(1, mon, kg = 60.0), mon.toEpochDay(), bench, RowState("", "8", 1))
        assertTrue(r is FormResult.Error)
        assertTrue(FolderLogic.editedEntry(e(1, mon), mon.toEpochDay(), bench, RowState("  ", "8", 1)) is FormResult.Error)
    }

    @Test fun editedEntryRefusesBulkOrMissingEntry() {
        assertTrue(FolderLogic.editedEntry(e(1, mon, sets = 3), mon.toEpochDay(), bench, RowState("60", "8", 1)) is FormResult.Error)
        assertTrue(FolderLogic.editedEntry(null, mon.toEpochDay(), bench, RowState("60", "8", 1)) is FormResult.Error)
    }

    @Test fun editedEntryKeepsIdNoteAndGroup() {
        val old = WorkoutEntry(5, mon.toEpochDay(), "Bench press", 1, 8, 60.0, "not", "GOGUS")
        val ok = FolderLogic.editedEntry(old, mon.toEpochDay(), bench, RowState("62,5", "6", 5)) as FormResult.Ok
        assertEquals(WorkoutEntry(5, mon.toEpochDay(), "Bench press", 1, 6, 62.5, "not", "GOGUS"), ok.entry)
    }

    @Test fun displayDoneSetsCapsOnlyWithBulk() {
        assertEquals(3, FolderLogic.displayDoneSets(4, 3, hasBulk = true))
        assertEquals(2, FolderLogic.displayDoneSets(2, 3, hasBulk = true))
        assertEquals(4, FolderLogic.displayDoneSets(4, 3, hasBulk = false))
    }

    // --- ipucu ---
    @Test fun lastKgIgnoresLaterDatesAndZeroKg() {
        val list = listOf(
            e(1, mon.minusWeeks(2), kg = 50.0), e(2, mon.minusWeeks(1), kg = 55.0),
            e(3, mon.minusWeeks(1), kg = 0.0),e(4, mon.plusWeeks(1), kg = 99.0),
        )
        assertEquals(55.0, FolderLogic.lastKg("Bench press", list, mon.toEpochDay())!!, 0.0)
        assertEquals(50.0, FolderLogic.lastKg("Bench press", list, mon.minusWeeks(1).toEpochDay() - 1)!!, 0.0)
        assertNull(FolderLogic.lastKg("Bench press", list, mon.minusWeeks(3).toEpochDay()))
    }

    @Test fun lastKgSameDayCountsAndPrefersNewestId() {
        val list = listOf(e(1, mon, kg = 60.0), e(2, mon, kg = 62.5))
        assertEquals(62.5, FolderLogic.lastKg("bench press", list, mon.toEpochDay())!!, 0.0)
    }

    // --- satir -> kayit ---
    @Test fun toEntryWritesSingleSetWithPlanGroupAndDate() {
        val r = FolderLogic.toEntry(0, mon.toEpochDay(), bench, RowState("62,5", "8"), null) as FormResult.Ok
        assertEquals(1, r.entry.sets)
        assertEquals(8, r.entry.reps)
        assertEquals(62.5, r.entry.weightKg, 0.0)
        assertEquals("GOGUS", r.entry.muscleGroup)
        assertEquals("Bench press", r.entry.exercise)
        assertEquals(mon.toEpochDay(), r.entry.epochDay)
    }

    @Test fun toEntryEmptyKgUsesHintElseZero() {
        assertEquals(55.0, ((FolderLogic.toEntry(0, 1, bench, RowState("", "8"), 55.0)) as FormResult.Ok).entry.weightKg, 0.0)
        assertEquals(0.0, ((FolderLogic.toEntry(0, 1, bench, RowState("", "8"), null)) as FormResult.Ok).entry.weightKg, 0.0)
    }

    @Test fun toEntryRejectsBadReps() {
        assertTrue(FolderLogic.toEntry(0, 1, bench, RowState("60", ""), null) is FormResult.Error)
        assertTrue(FolderLogic.toEntry(0, 1, bench, RowState("60", "0"), null) is FormResult.Error)
    }

    // --- gecen seferki doldurma ---
    @Test fun fillFillsOnlyUndoneRowsAndKeepsDone() {
        val rows = mapOf(
            key(0) to RowState("60", "8", 11),
            key(1) to RowState("", "8"),
            key(2) to RowState("", "8"),
        )
        val src = listOf(e(1, mon, kg = 50.0, reps = 10), e(2, mon, kg = 52.5, reps = 9))
        val (out, changed) = FolderLogic.fillFromSource(rows, listOf(bench), src)
        assertEquals(rows[key(0)], out[key(0)])
        assertEquals(RowState("52.5", "9"), out[key(1)])
        assertEquals(RowState("52.5", "9"), out[key(2)]) // kaynak bitince son deger
        assertEquals(2, changed)
    }

    @Test fun fillExpandsBulkSourceAndSkipsMissingExercise() {
        val rows = mapOf(key(0) to RowState("", "8"), key(0, row) to RowState("", "8"))
        val (out, changed) = FolderLogic.fillFromSource(rows, listOf(bench, row), listOf(e(1, mon, sets = 3, kg = 40.0)))
        assertEquals("40", out[key(0)]?.kg)
        assertEquals("", out[key(0, row)]?.kg)
        assertEquals(1, changed)
    }

    @Test fun fillWouldOverwriteOnlyTypedUndoneKg() {
        assertFalse(FolderLogic.fillWouldOverwrite(mapOf(key(0) to RowState("", "8"), key(1) to RowState("60", "8", 5))))
        assertTrue(FolderLogic.fillWouldOverwrite(mapOf(key(0) to RowState("45", "8"))))
    }

    @Test fun lastSourceDateIsClosestEarlierSameWeekday() {
        val entries = listOf(e(1, mon.minusWeeks(2)), e(2, mon.minusWeeks(1)), e(3, mon.plusDays(1)), e(4, mon))
        assertEquals(mon.minusWeeks(1), FolderLogic.lastSourceDate(1, mon, entries))
        assertNull(FolderLogic.lastSourceDate(1, mon.minusWeeks(2), entries))
    }

    // --- gecmis ozetleri ---
    @Test fun linesGroupSameRepsAndKg() {
        val list = listOf(e(1, mon), e(2, mon), e(3, mon), e(4, mon, name = "Lat pulldown", kg = 45.5, reps = 10))
        val lines = FolderLogic.lines(list)
        assertEquals(listOf("Bench press 3×8 @ 60", "Lat pulldown 1×10 @ 45.5"), lines.map { it.display })
    }

    @Test fun linesMixedSetsAndBodyweight() {
        val list = listOf(e(1, mon, kg = 60.0), e(2, mon, kg = 62.5, reps = 6), e(3, mon, name = "Plank", reps = 1, kg = 0.0))
        assertEquals(
            listOf("Bench press 1×8 @ 60 · 1×6 @ 62.5", "Plank 1×1"),
            FolderLogic.lines(list).map { it.display },
        )
    }

    @Test fun linesMergeLegacyNamesAndLargeKg() {
        val list = listOf(e(1, mon, name = "bench press", sets = 2, kg = 1250.0), e(2, mon, name = "Bench press", sets = 1, kg = 1250.0))
        assertEquals(listOf("Bench press 3×8 @ 1 250"), FolderLogic.lines(list).map { it.display })
    }

    @Test fun tonnageSumsSetsRepsKg() {
        assertEquals(60.0 * 8 * 3 + 40.0 * 10, FolderLogic.tonnage(listOf(e(1, mon, sets = 3), e(2, mon, reps = 10, kg = 40.0))), 0.0)
    }

    // --- aktivite ---
    private fun act(id: Long, d: LocalDate, t: ActivityType, m: Int) = ActivityLog(id, d.toEpochDay(), t, m)

    @Test fun activityHistoryGroupsByWeekdayNewestFirst() {
        val logs = listOf(
            act(1, mon, ActivityType.SAUNA, 10), act(2, mon.minusWeeks(1), ActivityType.YUZME, 40),
            act(3, mon.minusWeeks(1), ActivityType.SAUNA, 15), act(4, mon.plusDays(1), ActivityType.SAUNA, 5),
        )
        val h = FolderLogic.activityHistory(1, logs)
        assertEquals(listOf(mon, mon.minusWeeks(1)), h.map { it.first })
        assertEquals(listOf(2L, 3L), h[1].second.map { it.id })
    }

    @Test fun activityMinutesOnlyInsideWeekPerType() {
        val logs = listOf(
            act(1, mon, ActivityType.YUZME, 45), act(2, mon.plusDays(6), ActivityType.YUZME, 15),
            act(3, mon.plusDays(7), ActivityType.YUZME, 99), act(4, mon.minusDays(1), ActivityType.SAUNA, 99),
            act(5, mon.plusDays(2), ActivityType.SAUNA, 10),
        )
        val m = FolderLogic.activityMinutes(mon, logs)
        assertEquals(60, m[ActivityType.YUZME])
        assertEquals(10, m[ActivityType.SAUNA])
        assertEquals(0, m[ActivityType.BUHAR])
    }

    @Test fun deltaPercentNullWithoutPreviousAndRounds() {
        assertNull(FolderLogic.deltaPercent(100.0, 0.0))
        assertEquals(12, FolderLogic.deltaPercent(1120.0, 1000.0))
        assertEquals(-50, FolderLogic.deltaPercent(500.0, 1000.0))
        assertEquals(0, FolderLogic.deltaPercent(1000.0, 1000.0))
    }
}

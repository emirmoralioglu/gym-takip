package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsTest {
    private fun e(id: Long, day: Long, name: String, reps: Int, kg: Double) =
        WorkoutEntry(id, day, name, 3, reps, kg)

    @Test fun epleyFormula() {
        assertEquals(100.0, Records.epley1Rm(100.0, 1), 1e-9)
        assertEquals(130.0, Records.epley1Rm(100.0, 9), 1e-9) // 100 * (1 + 9/30) = 130
        assertEquals(60.0 * 1.5, Records.epley1Rm(60.0, 15), 1e-9)
    }

    @Test fun computePicksMaxWeightAndMax1RmSeparately() {
        val list = listOf(
            e(1, 10, "Squat", 10, 100.0), // 1RM 133.3
            e(2, 20, "Squat", 3, 110.0),  // 1RM 121, en agir
            e(3, 30, "squat ", 5, 90.0),  // ayni egzersiz (harf/bosluk farki)
        )
        val r = Records.compute(list).single()
        assertEquals(110.0, r.maxWeightKg, 0.0)
        assertEquals(20L, r.maxWeightDay)
        assertEquals(100.0 * (1 + 10 / 30.0), r.best1Rm, 1e-9)
        assertEquals(10L, r.best1RmDay)
    }

    @Test fun computeSkipsZeroWeightAndSortsByName() {
        val list = listOf(e(1, 1, "Şınav", 20, 0.0), e(2, 1, "Squat", 5, 80.0), e(3, 1, "Barfiks", 5, 10.0))
        assertEquals(listOf("Barfiks", "Squat"), Records.compute(list).map { it.exercise })
    }

    @Test fun heavierSetBreaksRecord() {
        val existing = listOf(e(1, 1, "Bench press", 5, 80.0))
        val n = Records.check(existing, e(0, 2, "Bench press", 5, 85.0))
        assertNotNull(n)
        assertTrue(n!!.weightBroken)
        assertEquals(80.0, n.prevWeightKg, 0.0)
    }

    @Test fun lighterButMoreRepsBreaksOnly1Rm() {
        val existing = listOf(e(1, 1, "Bench press", 5, 80.0)) // 1RM 93.3
        val n = Records.check(existing, e(0, 2, "Bench press", 12, 75.0))!! // 1RM 105
        assertFalse(n.weightBroken)
        assertTrue(n.oneRmBroken)
    }

    @Test fun equalOrLowerIsNotRecord() {
        val existing = listOf(e(1, 1, "Bench press", 5, 80.0))
        assertNull(Records.check(existing, e(0, 2, "Bench press", 5, 80.0)))
        assertNull(Records.check(existing, e(0, 2, "Bench press", 5, 70.0)))
    }

    @Test fun firstEverEntryIsNotCelebrated() {
        assertNull(Records.check(emptyList(), e(0, 1, "Bench press", 5, 80.0)))
        // baska egzersiz kaydi onceki sayilmaz
        assertNull(Records.check(listOf(e(1, 1, "Squat", 5, 200.0)), e(0, 2, "Bench press", 5, 80.0)))
    }

    @Test fun zeroWeightNeverRecord() {
        val existing = listOf(e(1, 1, "Şınav", 10, 0.0))
        assertNull(Records.check(existing, e(0, 2, "Şınav", 30, 0.0)))
    }

    @Test fun editingDoesNotCompareAgainstItself() {
        // Kayit 1'in kg'ini 80 -> 85 duzenlemek: eski satir (80) onceki sayilmamali, onceki yok -> rekor degil.
        val existing = listOf(e(1, 1, "Bench press", 5, 80.0))
        assertNull(Records.check(existing, e(1, 1, "Bench press", 5, 85.0)))
        // Baska bir kayit varsa onunla karsilastirilir.
        val two = existing + e(2, 0, "Bench press", 5, 82.0)
        val n = Records.check(two, e(1, 1, "Bench press", 5, 85.0))!!
        assertEquals(82.0, n.prevWeightKg, 0.0)
    }
}

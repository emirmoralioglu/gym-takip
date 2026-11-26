package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryFormTest {
    private fun p(name: String = "Bench", sets: String = "3", reps: String = "10", w: String = "60", id: Long = 0) =
        EntryForm.parse(id, 100, name, sets, reps, w, " not ")

    @Test fun validParses() {
        val r = p() as FormResult.Ok
        assertEquals(3, r.entry.sets)
        assertEquals(60.0, r.entry.weightKg, 0.0)
        assertEquals("not", r.entry.note)
    }

    @Test fun commaDecimalAccepted() {
        assertEquals(62.5, (p(w = "62,5") as FormResult.Ok).entry.weightKg, 0.0)
    }

    @Test fun editKeepsId() {
        assertEquals(7L, (p(id = 7) as FormResult.Ok).entry.id)
    }

    @Test fun muscleGroupStoredOnlyWhenKnown() {
        val ok = EntryForm.parse(0, 1, "Squat", "3", "5", "100", "", "BACAK") as FormResult.Ok
        assertEquals("BACAK", ok.entry.muscleGroup)
        val bad = EntryForm.parse(0, 1, "Squat", "3", "5", "100", "", "UYDURMA") as FormResult.Ok
        assertEquals("", bad.entry.muscleGroup)
        assertEquals("", (p() as FormResult.Ok).entry.muscleGroup)
    }

    @Test fun upperLimitsRejected() {
        assertTrue(p(sets = "101") is FormResult.Error)
        assertTrue(p(reps = "1001") is FormResult.Error)
        assertTrue(p(w = "1000,5") is FormResult.Error)
        assertTrue(p(name = "x".repeat(61)) is FormResult.Error)
        assertTrue(p(sets = "100", reps = "1000", w = "1000") is FormResult.Ok)
    }

    @Test fun invalidInputsRejected() {
        assertTrue(p(name = "  ") is FormResult.Error)
        assertTrue(p(sets = "0") is FormResult.Error)
        assertTrue(p(sets = "abc") is FormResult.Error)
        assertTrue(p(reps = "") is FormResult.Error)
        assertTrue(p(w = "-5") is FormResult.Error)
        assertTrue(p(w = "NaN") is FormResult.Error)
    }

    @Test fun nameLimitMatchesCustomExerciseLimit() {
        assertEquals(60, ExerciseCatalog.MAX_NAME_LENGTH)
        fun q(n: String) = EntryForm.parse(0, 1, n, "3", "10", "20", "")
        assertTrue(q("x".repeat(60)) is FormResult.Ok)
        assertTrue(q("x".repeat(61)) is FormResult.Error)
    }
}

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

    @Test fun invalidInputsRejected() {
        assertTrue(p(name = "  ") is FormResult.Error)
        assertTrue(p(sets = "0") is FormResult.Error)
        assertTrue(p(sets = "abc") is FormResult.Error)
        assertTrue(p(reps = "") is FormResult.Error)
        assertTrue(p(w = "-5") is FormResult.Error)
        assertTrue(p(w = "NaN") is FormResult.Error)
    }
}

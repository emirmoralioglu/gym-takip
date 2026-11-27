package com.emir.gym.assistant

import com.emir.gym.data.MealType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T2 = LocalDate.of(2026, 11, 27)
private fun meal2(prot: Double = 30.0, kcal: Int = 500, desc: String = "Tavuk") =
    ActionPayload.AddMeal(T2.toEpochDay(), MealType.OGLE, desc, prot, kcal, 40.0, 10.0)

class TextCleanAndRoundingTest {
    private val day = T2.toEpochDay()

    @Test fun directionalAndInvisibleCharsAreRemoved() {
        val dirty = "Tav‮ık‎ ​pi⁦la⁩v‏﻿\u0007\n  x"
        assertEquals("Tavık pilav x", TextClean.clean(dirty))
        assertEquals("a b", TextClean.clean("a\tb"))
        assertEquals("çğıöşüİ", TextClean.clean("çğıöşüİ"))
    }

    @Test fun validatorDropsThemFromDescriptionAndNote() {
        val args = Js.parse("{\"meal_type\":\"ogle\",\"description\":\"Pi\\u202Elav\\u200B\",\"protein_g\":10,\"calories_kcal\":100}")!!
        val m = (ToolValidator.validate(ToolDefs.ADD_MEAL, args, T2) as Validation.Ok).payload as ActionPayload.AddMeal
        assertEquals("Pilav", m.description)
        val act = Js.parse("{\"type\":\"YUZME\",\"minutes\":10,\"note\":\"ha\\u2067vuz\\u200F\"}")!!
        val a = (ToolValidator.validate(ToolDefs.LOG_ACTIVITY, act, T2) as Validation.Ok).payload as ActionPayload.LogActivity
        assertEquals("havuz", a.note)
    }

    @Test fun editNormalizeDropsThemToo() = runBlocking {
        val store = FakeActionStore(); val w = RecordingWriter()
        val id = store.insert(meal2(), 1L)
        confirmEditedWith(store, w, id, meal2(desc = "Pi‮lav​"))
        assertEquals("Pilav", (w.written.single() as ActionPayload.AddMeal).description)
        val id2 = store.insert(meal2(), 1L)
        assertTrue(confirmEditedWith(store, w, id2, meal2(desc = "​‮")) is ConfirmResult.Failed)
    }

    @Test fun weightRejectsNanAndInfinity() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertNotNull("$it", ActionEdit.check(ActionPayload.LogWeight(day, it), T2))
        }
        assertNull(ActionEdit.check(ActionPayload.LogWeight(day, 80.0), T2))
        assertNotNull(ActionEdit.check(meal2(prot = Double.NaN), T2))
        assertNotNull(ActionEdit.check(meal2().copy(carbsG = Double.POSITIVE_INFINITY), T2))
    }

    @Test fun editRoundingMatchesValidator() {
        for (v in listOf(12.34, 12.35, 0.05, 299.949, 45.55, 7.0)) {
            val args = Js.parse("{\"meal_type\":\"ogle\",\"description\":\"x\",\"protein_g\":$v,\"calories_kcal\":10,\"carbs_g\":$v,\"fat_g\":$v}")!!
            val viaTool = (ToolValidator.validate(ToolDefs.ADD_MEAL, args, T2) as Validation.Ok).payload as ActionPayload.AddMeal
            val viaEdit = ActionEdit.normalize(ActionPayload.AddMeal(day, MealType.OGLE, "x", v, 10, v, v)) as ActionPayload.AddMeal
            assertEquals("$v", viaTool, viaEdit)
        }
        for (v in listOf(80.456, 80.455, 0.5, 99.999)) {
            val args = Js.parse("{\"kg\":$v}")!!
            val viaTool = (ToolValidator.validate(ToolDefs.LOG_BODY_WEIGHT, args, T2) as Validation.Ok).payload
            assertEquals("$v", viaTool, ActionEdit.normalize(ActionPayload.LogWeight(day, v)))
        }
    }
}

class ChatBusyTest {
    @Test fun anyActivityBlocksNewWork() {
        assertTrue(ChatBusy.idle(false, false, false))
        assertFalse(ChatBusy.idle(true, false, false))
        assertFalse(ChatBusy.idle(false, true, false))
        assertFalse(ChatBusy.idle(false, false, true))
    }
}

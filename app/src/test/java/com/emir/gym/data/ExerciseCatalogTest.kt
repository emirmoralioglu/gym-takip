package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogTest {
    @Test fun catalogHasEnoughUniqueExercisesInEveryGroup() {
        assertTrue(ExerciseCatalog.all.size >= 40)
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.all.map { ExerciseCatalog.key(it.name) }.toSet().size)
        MuscleGroup.entries.forEach { g -> assertTrue(g.name, ExerciseCatalog.byGroup()[g].orEmpty().size >= 6) }
    }

    @Test fun groupLookupUsesStoredGroupThenCatalogThenCustom() {
        val custom = listOf(CustomExercise("Landmine press", "OMUZ"))
        val stored = WorkoutEntry(1, 1, "Squat", 1, 1, 1.0, "", "KARIN")
        assertEquals(MuscleGroup.KARIN, ExerciseCatalog.groupOf(stored, custom))
        val legacy = WorkoutEntry(2, 1, " bench PRESS", 1, 1, 1.0)
        assertEquals(MuscleGroup.GOGUS, ExerciseCatalog.groupOf(legacy, custom))
        assertEquals(MuscleGroup.OMUZ, ExerciseCatalog.groupOfName("landmine press", custom))
        assertNull(ExerciseCatalog.groupOfName("Bilinmeyen", custom))
    }

    @Test fun customValidation() {
        val custom = listOf(CustomExercise("Landmine press", "OMUZ"))
        assertNull(ExerciseCatalog.validateCustom("Kettlebell swing", MuscleGroup.BACAK, custom))
        assertNotNull(ExerciseCatalog.validateCustom("  ", MuscleGroup.BACAK, custom))
        assertNotNull(ExerciseCatalog.validateCustom("Kettlebell swing", null, custom))
        assertNotNull(ExerciseCatalog.validateCustom("squat", MuscleGroup.BACAK, custom)) // katalogda var
        assertNotNull(ExerciseCatalog.validateCustom("LANDMINE PRESS", MuscleGroup.OMUZ, custom)) // ozelde var
        assertNotNull(ExerciseCatalog.validateCustom("x".repeat(41), MuscleGroup.OMUZ, custom))
    }

    @Test fun turkishCaseFolding() {
        assertEquals(ExerciseCatalog.key("IŞIK"), ExerciseCatalog.key("ışık"))
        assertEquals(ExerciseCatalog.key("İNCE"), ExerciseCatalog.key("ince"))
        assertEquals(ExerciseCatalog.key("INCLINE press"), ExerciseCatalog.key("incline PRESS"))
    }
}

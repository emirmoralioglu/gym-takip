package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ingilizce hareket adlari, eski Turkce adlara takma ad ve plan-katalog tutarliligi. */
class LegacyNamesTest {
    @Test fun planNamesExistInCatalogWithSameGroup() {
        DefaultPlan.exercises.forEach { p ->
            val c = ExerciseCatalog.all.singleOrNull { it.name == p.name }
            assertNotNull("katalogda yok: ${p.name}", c)
            assertEquals(p.name, p.muscleGroup, c!!.group.name)
        }
    }

    @Test fun planNamesMatchUserPlanText() {
        val expected = setOf(
            "Chest press machine", "Lat pulldown", "Seated cable row", "Shoulder press machine",
            "Reverse pec deck", "Leg press", "Leg curl", "Leg extension", "Calf raise",
            "Incline chest press machine", "Lateral raise", "Face pull", "Hack squat",
            "Romanian deadlift", "Hip abduction",
        )
        assertEquals(expected, DefaultPlan.exercises.map { it.name }.toSet())
    }

    @Test fun legacyRenamesAreConsistent() {
        val olds = LEGACY_EXERCISE_RENAMES.keys
        val news = LEGACY_EXERCISE_RENAMES.values
        assertTrue("zincirleme yeniden adlandirma", olds.intersect(news.toSet()).isEmpty())
        assertEquals(news.size, news.toSet().size)
        news.forEach { n -> assertTrue("katalogda yok: $n", ExerciseCatalog.all.any { it.name == n }) }
        olds.forEach { o -> assertTrue("eski ad katalogda kalmis: $o", ExerciseCatalog.all.none { it.name == o }) }
    }

    @Test fun legacyTurkishNamesAliasToNewNames() {
        LEGACY_EXERCISE_RENAMES.forEach { (old, new) ->
            assertEquals(old, ExerciseCatalog.key(new), ExerciseCatalog.key(old))
        }
        assertEquals(ExerciseCatalog.key("Calf raise"), ExerciseCatalog.key("  BALDIR KALDIRMA "))
        assertEquals(MuscleGroup.BACAK, ExerciseCatalog.groupOfName("Baldır kaldırma", emptyList()))
        assertEquals(MuscleGroup.KARDIYO, ExerciseCatalog.groupOfName("İP ATLAMA", emptyList()))
        assertNotNull(ExerciseCatalog.validateCustom("Şınav", MuscleGroup.GOGUS, emptyList()))
    }

    @Test fun noTurkishLettersLeftInCatalogNames() {
        assertTrue(ExerciseCatalog.all.none { d -> d.name.any { it in "ğüşıöçĞÜŞİÖÇ" } })
    }
}

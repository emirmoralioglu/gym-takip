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

    /** master'daki eski katalogun tum adlari (sabit liste). */
    private val oldCatalogNames = listOf(
        "Bench press", "Eğimli bench press", "Dambıl göğüs press", "Eğimli dambıl press",
        "Dambıl fly", "Kablo crossover", "Şınav", "Paralel bar dips",
        "Chest press makinesi", "Incline chest press makinesi",
        "Deadlift", "Lat pulldown", "Barfiks", "Barbell row", "Tek kol dambıl row",
        "Oturarak kablo row", "T-bar row", "Sırt ekstansiyonu",
        "Overhead press", "Dambıl omuz press", "Yan lateral raise", "Ön raise",
        "Arka omuz fly", "Face pull", "Omuz silkme (shrug)", "Shoulder press makinesi", "Reverse pec deck",
        "Barbell curl", "Dambıl curl", "Çekiç curl", "Preacher curl",
        "Triceps pushdown", "Skull crusher", "French press", "Bank dips",
        "Squat", "Ön squat", "Leg press", "Romanian deadlift", "Hamle (lunge)",
        "Bulgarian split squat", "Leg extension", "Leg curl", "Baldır kaldırma", "Hip thrust",
        "Hack squat", "Hip abduction",
        "Crunch", "Plank", "Bacak kaldırma", "Ters crunch", "Kablo crunch", "Russian twist", "Ab wheel",
        "Koşu bandı", "Bisiklet", "Eliptik", "Kürek makinesi", "İp atlama", "Yürüyüş",
    )

    @Test fun everyOldCatalogNameIsStillInCatalogOrMapped() {
        assertEquals(oldCatalogNames.size, oldCatalogNames.toSet().size)
        val current = ExerciseCatalog.all.map { it.name }.toSet()
        oldCatalogNames.filter { it !in current }.forEach { old ->
            val new = LEGACY_EXERCISE_RENAMES[old]
            assertNotNull("eslemesi yok: $old", new)
            assertTrue("yeni ad katalogda yok: $new", new in current)
        }
    }

    @Test fun customValidationPointsLegacyNameToNewName() {
        assertEquals("Bu hareket artık Cycling adıyla listede", ExerciseCatalog.validateCustom("Bisiklet", MuscleGroup.KARDIYO, emptyList()))
        assertEquals("Bu hareket artık Pull-up adıyla listede", ExerciseCatalog.validateCustom(" barfiks ", MuscleGroup.SIRT, emptyList()))
    }

    @Test fun recordsAndChartShowCanonicalEnglishNames() {
        val e = WorkoutEntry(1, 100, "  baldır kaldırma ", 3, 8, 50.0)
        assertEquals("Calf raise", Records.compute(listOf(e)).single().exercise)
        val prev = WorkoutEntry(1, 100, "Calf raise", 3, 8, 50.0)
        val nr = Records.check(listOf(prev), WorkoutEntry(0, 101, "BALDIR KALDIRMA", 3, 8, 60.0))!!
        assertEquals("Calf raise", nr.exercise)
        assertEquals(listOf("Calf raise"), ProgressChart.exerciseNames(listOf(e)))
        assertEquals("Özel hareket", ExerciseCatalog.displayName("  Özel hareket "))
    }

    @Test fun planNamesAreEnglishAndOverrideUserPlanTextWhereTheyConflict() {
        // Kural: tum hareket adlari Ingilizce. plan_metin.txt ile celisen yerlerde
        // (Chest press makinesi -> Chest press machine, Face pulls -> Face pull,
        // Incline chest press makinesi -> ..machine, Shoulder press makinesi -> ..machine,
        // Lateral raise (makine/cable) -> Lateral raise) Ingilizce kural kazanir.
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

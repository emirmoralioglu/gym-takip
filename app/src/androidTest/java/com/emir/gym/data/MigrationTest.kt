package com.emir.gym.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsAllRowsAndAddsDefaults() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note) VALUES (1, 20000, 'Bench', 3, 10, 62.5, 'not')")
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note) VALUES (7, 20001, 'Squat', 5, 5, 100.0, '')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup FROM workout_entries ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0)); assertEquals(20000L, c.getLong(1)); assertEquals("Bench", c.getString(2))
            assertEquals(3, c.getInt(3)); assertEquals(10, c.getInt(4)); assertEquals(62.5, c.getDouble(5), 0.0)
            assertEquals("not", c.getString(6)); assertEquals("", c.getString(7))
            c.moveToNext()
            assertEquals(7L, c.getLong(0)); assertEquals("Squat", c.getString(2)); assertEquals(100.0, c.getDouble(5), 0.0)
        }
        // Yeni tablo kullanilabilir ve autoincrement sayaci eski satirlarin onunde.
        db.execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Landmine press', 'OMUZ')")
        db.query("SELECT COUNT(*) FROM custom_exercises").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }

    @Test
    fun migrate1To2WithEmptyDatabase() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()
    }

    @Test
    fun migrate2To3KeepsRowsSeedsPlanAndNewTablesAreWritable() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup) VALUES (3, 20000, 'Bench press', 3, 10, 62.5, 'not', 'GOGUS')")
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup) VALUES (9, 20001, 'Squat', 5, 5, 100.0, '', '')")
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Landmine press', 'OMUZ')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        // Eski satirlar aynen duruyor.
        db.query("SELECT id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup FROM workout_entries ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(3L, c.getLong(0)); assertEquals(20000L, c.getLong(1)); assertEquals("Bench press", c.getString(2))
            assertEquals(3, c.getInt(3)); assertEquals(10, c.getInt(4)); assertEquals(62.5, c.getDouble(5), 0.0)
            assertEquals("not", c.getString(6)); assertEquals("GOGUS", c.getString(7))
            c.moveToNext()
            assertEquals(9L, c.getLong(0)); assertEquals("Squat", c.getString(2)); assertEquals("", c.getString(7))
        }
        db.query("SELECT name, muscleGroup FROM custom_exercises").use { c ->
            assertEquals(1, c.count); c.moveToFirst(); assertEquals("Landmine press", c.getString(0))
        }

        // Plan tohumu: 7 gun, gun basina dogru hareket sayisi.
        db.query("SELECT COUNT(*) FROM plan_days").use { c -> c.moveToFirst(); assertEquals(7, c.getInt(0)) }
        val counts = mutableMapOf<Int, Int>()
        db.query("SELECT weekday, COUNT(*) FROM plan_exercises GROUP BY weekday").use { c ->
            while (c.moveToNext()) counts[c.getInt(0)] = c.getInt(1)
        }
        assertEquals(mapOf(1 to 5, 2 to 4, 4 to 4, 5 to 4), counts)
        db.query("SELECT type FROM plan_days ORDER BY weekday").use { c ->
            val types = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            assertEquals(listOf("AGIRLIK", "AGIRLIK", "YUZME", "AGIRLIK", "AGIRLIK", "YUZME", "DINLENME"), types)
        }
        db.query("SELECT name, sets, targetReps, restSeconds, posture FROM plan_exercises WHERE weekday = 1 ORDER BY position").use { c ->
            c.moveToFirst()
            assertEquals("Chest press machine", c.getString(0)); assertEquals(3, c.getInt(1))
            assertEquals(8, c.getInt(2)); assertEquals(90, c.getInt(3)); assertEquals(0, c.getInt(4))
            c.moveToLast(); assertEquals("Reverse pec deck", c.getString(0)); assertEquals(1, c.getInt(4))
        }

        // Yeni tablolara yazilabiliyor.
        db.execSQL("INSERT INTO activity_logs (epochDay, type, minutes, distanceM, note) VALUES (20002, 'YUZME', 45, 1200, '')")
        db.execSQL("INSERT INTO activity_logs (epochDay, type, minutes, distanceM, note) VALUES (20002, 'SAUNA', 10, NULL, '')")
        db.execSQL("INSERT INTO meals (epochDay, mealType, description, proteinG, calories, carbsG, fatG) VALUES (20002, 'OGLE', 'tavuk', 40.5, 600, NULL, NULL)")
        db.execSQL("INSERT INTO water_logs (epochDay, ml) VALUES (20002, 500)")
        db.execSQL("INSERT INTO body_weights (epochDay, kg) VALUES (20002, 75.5)")
        listOf("activity_logs" to 2, "meals" to 1, "water_logs" to 1, "body_weights" to 1).forEach { (t, n) ->
            db.query("SELECT COUNT(*) FROM $t").use { c -> c.moveToFirst(); assertEquals(t, n, c.getInt(0)) }
        }
    }

    @Test
    fun migrate2To3WithEmptyDatabaseStillSeeds() {
        helper.createDatabase(dbName, 2).close()
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM plan_exercises").use { c -> c.moveToFirst(); assertEquals(17, c.getInt(0)) }
    }

    @Test
    fun migrateAllTheWayFrom1() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note) VALUES (1, 20000, 'Bench', 3, 10, 62.5, 'x')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM workout_entries").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM plan_days").use { c -> c.moveToFirst(); assertEquals(7, c.getInt(0)) }
    }
}

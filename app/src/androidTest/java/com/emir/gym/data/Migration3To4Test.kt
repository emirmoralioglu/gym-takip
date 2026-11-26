package com.emir.gym.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Migration3To4Test {
    private val dbName = "migration-3-4-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun renamesLegacyNamesAndKeepsEverythingElse() {
        helper.createDatabase(dbName, 3).apply {
            val ins = "INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup) VALUES "
            execSQL(ins + "(1, 20000, 'Baldır kaldırma', 3, 10, 40.0, 'a', 'BACAK')")
            execSQL(ins + "(2, 20000, 'Oturarak kablo row', 3, 8, 50.0, '', 'SIRT')")
            execSQL(ins + "(3, 20001, 'Bench press', 3, 8, 60.0, '', 'GOGUS')")
            execSQL(ins + "(4, 20001, 'Benim hareketim', 2, 12, 5.0, '', 'KOL')")
            execSQL(ins + "(5, 20002, 'baldır kaldırma', 1, 1, 1.0, '', '')") // harf katlamasiz: degismez
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Landmine press', 'OMUZ')")
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Şınav duvar', 'GOGUS')")
            val pi = "INSERT INTO plan_exercises (weekday, position, name, muscleGroup, sets, targetReps, restSeconds, note, posture) VALUES "
            execSQL(pi + "(2, 0, 'Leg press', 'BACAK', 3, 8, 90, '', 0)")
            execSQL(pi + "(2, 1, 'Baldır kaldırma', 'BACAK', 3, 8, 90, '', 0)")
            execSQL(pi + "(4, 0, 'Yan lateral raise', 'OMUZ', 3, 8, 90, 'n', 0)")
            execSQL(pi + "(4, 1, 'Özel plan hareketi', 'KOL', 3, 8, 90, '', 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        fun names(sql: String) =
            db.query(sql).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
        assertEquals(
            listOf("Calf raise", "Seated cable row", "Bench press", "Benim hareketim", "baldır kaldırma"),
            names("SELECT exercise FROM workout_entries ORDER BY id"),
        )
        assertEquals(listOf("Landmine press", "Şınav duvar"), names("SELECT name FROM custom_exercises ORDER BY name"))
        assertEquals(
            listOf("Leg press", "Calf raise", "Lateral raise", "Özel plan hareketi"),
            names("SELECT name FROM plan_exercises ORDER BY weekday, position"),
        )
        db.query("SELECT COUNT(*), SUM(sets), SUM(weightKg) FROM workout_entries").use { c ->
            c.moveToFirst(); assertEquals(5, c.getInt(0)); assertEquals(12, c.getInt(1)); assertEquals(156.0, c.getDouble(2), 0.0)
        }
        db.query("SELECT COUNT(*) FROM plan_exercises").use { c -> c.moveToFirst(); assertEquals(4, c.getInt(0)) }
        db.query("SELECT muscleGroup, note FROM workout_entries WHERE id = 1").use { c ->
            c.moveToFirst(); assertEquals("BACAK", c.getString(0)); assertEquals("a", c.getString(1))
        }
        db.query("SELECT note, muscleGroup FROM plan_exercises WHERE name = 'Lateral raise'").use { c ->
            c.moveToFirst(); assertEquals("n", c.getString(0)); assertEquals("OMUZ", c.getString(1))
        }
    }

    @Test
    fun customNameCollisionDoesNotCrashOrDeleteRows() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Şınav', 'GOGUS')")
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Push-up', 'GOGUS')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
        db.query("SELECT COUNT(*) FROM custom_exercises").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
    }

    @Test
    fun v2SeedAndFullChainFrom1() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note) VALUES (1, 20000, 'Baldır kaldırma', 3, 10, 62.5, 'x')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        db.query("SELECT exercise FROM workout_entries").use { c -> c.moveToFirst(); assertEquals("Calf raise", c.getString(0)) }
        db.query("SELECT COUNT(*) FROM plan_exercises").use { c -> c.moveToFirst(); assertEquals(17, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM plan_exercises WHERE name = 'Calf raise'").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
    }
}

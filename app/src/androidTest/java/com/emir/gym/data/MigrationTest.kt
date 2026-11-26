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
}

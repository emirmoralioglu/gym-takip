package com.emir.gym.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Migration4To5Test {
    private val dbName = "migration-4-5-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v4DataSurvivesAndChatTablesAreCreated() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO workout_entries (id, epochDay, exercise, sets, reps, weightKg, note, muscleGroup) VALUES (1, 20000, 'Bench press', 3, 8, 60.0, 'n', 'GOGUS')")
            execSQL("INSERT INTO custom_exercises (name, muscleGroup) VALUES ('Landmine press', 'OMUZ')")
            execSQL("INSERT INTO plan_days (weekday, title, type, note) VALUES (6, 'Yuzme', 'YUZME', '')")
            execSQL("INSERT INTO plan_exercises (weekday, position, name, muscleGroup, sets, targetReps, restSeconds, note, posture) VALUES (1, 0, 'Squat', 'BACAK', 3, 8, 90, '', 0)")
            execSQL("INSERT INTO activity_logs (id, epochDay, type, minutes, distanceM, note) VALUES (4, 20001, 'YUZME', 40, 1200, 'x')")
            execSQL("INSERT INTO meals (id, epochDay, mealType, description, proteinG, calories, carbsG, fatG) VALUES (5, 20001, 'OGLE', 'Pilav', 30.5, 600, 70.0, NULL)")
            execSQL("INSERT INTO water_logs (id, epochDay, ml) VALUES (6, 20001, 250)")
            execSQL("INSERT INTO body_weights (epochDay, kg) VALUES (20001, 79.5)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        fun one(sql: String) = db.query(sql).use { c -> c.moveToFirst(); c.getString(0) }
        assertEquals("1", one("SELECT COUNT(*) FROM workout_entries"))
        assertEquals("Bench press", one("SELECT exercise FROM workout_entries WHERE id = 1"))
        assertEquals("Landmine press", one("SELECT name FROM custom_exercises"))
        assertEquals("Yuzme", one("SELECT title FROM plan_days WHERE weekday = 6"))
        assertEquals("Squat", one("SELECT name FROM plan_exercises"))
        assertEquals("1200", one("SELECT distanceM FROM activity_logs WHERE id = 4"))
        assertEquals("30.5", one("SELECT proteinG FROM meals WHERE id = 5"))
        assertEquals("250", one("SELECT ml FROM water_logs WHERE id = 6"))
        assertEquals("79.5", one("SELECT kg FROM body_weights"))
        // Yeni tablolar bos ve yazilabilir.
        assertEquals("0", one("SELECT COUNT(*) FROM chat_messages"))
        assertEquals("0", one("SELECT COUNT(*) FROM pending_actions"))
        db.execSQL("INSERT INTO chat_messages (createdAt, role, contentJson, imagePath, provider, model) VALUES (1, 'USER', '{}', NULL, 'GEMINI', 'm')")
        db.execSQL("INSERT INTO pending_actions (createdAt, type, payloadJson, status, resultRef) VALUES (1, 'add_water', '{}', 'PENDING', NULL)")
        assertEquals("1", one("SELECT id FROM chat_messages"))
        assertEquals("PENDING", one("SELECT status FROM pending_actions"))
    }
}

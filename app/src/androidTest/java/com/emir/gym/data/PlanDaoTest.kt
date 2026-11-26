package com.emir.gym.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Yeni kurulum (onCreate tohumu) ve plan duzenleme DAO'lari. */
class PlanDaoTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(AppDatabase.seedOnCreate).build()
    }

    @After fun close() = db.close()

    @Test fun freshInstallIsSeeded() = runBlocking {
        val dao = db.planDao()
        assertEquals(7, dao.observeDays().first().size)
        val counts = dao.observeExercises().first().groupingBy { it.weekday }.eachCount()
        assertEquals(mapOf(1 to 5, 2 to 4, 4 to 4, 5 to 4), counts)
    }

    @Test fun addRemoveReorderUpdate() = runBlocking {
        val dao = db.planDao()
        val newId = dao.add(PlanExercise(weekday = 3, position = 99, name = "Yüzme ısınma", muscleGroup = "KARDIYO"))
        assertEquals(listOf(0), dao.exercisesOf(3).map { it.position })

        val mon = dao.exercisesOf(1)
        assertEquals(5, mon.size)
        dao.reorder(1, mon.map { it.id }.reversed())
        assertEquals(mon.map { it.id }.reversed(), dao.exercisesOf(1).map { it.id })

        val first = dao.exercisesOf(1).first()
        dao.update(first.copy(sets = 4, note = "değişti"))
        assertEquals(4, dao.exercisesOf(1).first().sets)

        dao.remove(dao.exercisesOf(1)[1])
        assertEquals(listOf(0, 1, 2, 3), dao.exercisesOf(1).map { it.position })

        dao.remove(dao.exercisesOf(3).single { it.id == newId })
        assertTrue(dao.exercisesOf(3).isEmpty())

        val bad = runCatching { dao.reorder(1, listOf(1L, 2L)) }
        assertTrue(bad.isFailure)
    }

    @Test fun trackingTablesRoundTrip() = runBlocking {
        val t = db.trackingDao()
        t.upsertActivity(ActivityLog(epochDay = 10, type = ActivityType.YUZME, minutes = 45, distanceM = 1500))
        t.upsertMeal(Meal(epochDay = 10, mealType = MealType.AKSAM, proteinG = 35.5, calories = 700))
        t.upsertWater(WaterLog(epochDay = 10, ml = 500))
        t.upsertBodyWeight(BodyWeight(10, 75.0))
        t.upsertBodyWeight(BodyWeight(10, 75.4)) // ayni gun: degistirir
        assertEquals(1500, t.observeActivities().first().single().distanceM)
        assertEquals(MealType.AKSAM, t.observeMeals().first().single().mealType)
        assertEquals(500, t.observeWater().first().single().ml)
        assertEquals(75.4, t.observeBodyWeights().first().single().kg, 0.0)
    }
}

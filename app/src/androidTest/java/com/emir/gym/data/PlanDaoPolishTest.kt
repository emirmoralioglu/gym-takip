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

/** PlanDao dogrulama ve sira butunlugu. */
class PlanDaoPolishTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(AppDatabase.seedOnCreate).build()
    }

    @After fun close() = db.close()

    @Test fun weekdayMustBeInRange() = runBlocking {
        val dao = db.planDao()
        listOf(0, 8, -1).forEach { d ->
            assertTrue(runCatching { dao.add(PlanExercise(weekday = d, position = 0, name = "x", muscleGroup = "KOL")) }.isFailure)
            assertTrue(runCatching { dao.upsertDay(PlanDay(d, "x", PlanDayType.AGIRLIK)) }.isFailure)
        }
        val first = dao.exercisesOf(1).first()
        assertTrue(runCatching { dao.update(first.copy(weekday = 9)) }.isFailure)
        assertEquals(1, dao.exercisesOf(1).first { it.id == first.id }.weekday)
        assertEquals(7, dao.observeDays().first().size)
        assertEquals(17, dao.observeExercises().first().size)
    }

    @Test fun updateKeepsPositionAndNeverCollides() = runBlocking {
        val dao = db.planDao()
        val mon = dao.exercisesOf(1)
        dao.update(mon[3].copy(position = 0, name = "Yeni ad"))
        val after = dao.exercisesOf(1)
        assertEquals(listOf(0, 1, 2, 3, 4), after.map { it.position })
        assertEquals(mon.map { it.id }, after.map { it.id })
        assertEquals("Yeni ad", after[3].name)
    }

    @Test fun updateMovingDayAppendsAndCompactsBothDays() = runBlocking {
        val dao = db.planDao()
        val moved = dao.exercisesOf(1)[1]
        dao.update(moved.copy(weekday = 2, position = 0))
        assertEquals(listOf(0, 1, 2, 3), dao.exercisesOf(1).map { it.position })
        val tue = dao.exercisesOf(2)
        assertEquals(listOf(0, 1, 2, 3, 4), tue.map { it.position })
        assertEquals(moved.id, tue.last().id)
    }

    @Test fun removeUsesStoredWeekdayNotCallerObject() = runBlocking {
        val dao = db.planDao()
        val stale = dao.exercisesOf(1)[0].copy(weekday = 2) // yanlis gun iddia eden nesne
        dao.remove(stale)
        assertEquals(listOf(0, 1, 2, 3), dao.exercisesOf(1).map { it.position })
        assertEquals(listOf(0, 1, 2, 3), dao.exercisesOf(2).map { it.position })
        dao.remove(stale) // zaten yok: sessizce gecer
        assertEquals(4, dao.exercisesOf(1).size)
        assertEquals(4, dao.exercisesOf(2).size)
    }

    @Test fun freshSeedUsesEnglishNames() = runBlocking {
        val names = db.planDao().exercisesOf(5).map { it.name }
        assertEquals(listOf("Hack squat", "Romanian deadlift", "Hip abduction", "Calf raise"), names)
    }
}

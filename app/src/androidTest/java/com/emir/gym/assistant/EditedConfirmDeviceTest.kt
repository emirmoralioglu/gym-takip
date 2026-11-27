package com.emir.gym.assistant

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.MealType
import com.emir.gym.data.NutritionGoals
import com.emir.gym.data.PendingStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Duzenlenmis onay: gercek Room uzerinde atomik yazim, ref'te duzenlenmis veri, sinir ihlalinde hicbir sey yazilmaz. */
class EditedConfirmDeviceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AssistantRepository
    private val day = LocalDate.now().toEpochDay()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = AssistantRepository(db, { NutritionGoals() })
    }

    @After fun close() { db.close() }

    private fun meal(prot: Double) = ActionPayload.AddMeal(day, MealType.OGLE, "Pilav", prot, 600, null, null)

    @Test fun editedValueIsWrittenAndRemembered() = runBlocking {
        val id = repo.actions.insert(meal(30.0), 1L)
        val r = repo.confirmEdited(id, meal(41.5))
        assertTrue(r.toString(), r is ConfirmResult.Confirmed)
        val rows = db.trackingDao().observeMeals().first()
        assertEquals(1, rows.size)
        assertEquals(41.5, rows[0].proteinG, 0.0)
        val a = db.chatDao().action(id)!!
        assertEquals(PendingStatus.CONFIRMED, a.status)
        val back = ActionRef.edited(a.type, a.resultRef) as ActionPayload.AddMeal
        assertEquals(41.5, back.proteinG, 0.0)
        assertEquals("meal:${rows[0].id}", ActionRef.parse(a.resultRef)!!.ref)
        // Cift dokunus: ikinci onay yeni kayit yazmaz.
        assertTrue(repo.confirmEdited(id, meal(50.0)) is ConfirmResult.AlreadyConfirmed)
        assertTrue(repo.confirm(id) is ConfirmResult.AlreadyConfirmed)
        assertEquals(1, db.trackingDao().observeMeals().first().size)
    }

    @Test fun limitViolationWritesNothingAndStaysPending() = runBlocking {
        val id = repo.actions.insert(meal(30.0), 1L)
        assertTrue(repo.confirmEdited(id, meal(999.0)) is ConfirmResult.Failed)
        assertEquals(0, db.trackingDao().observeMeals().first().size)
        assertEquals(PendingStatus.PENDING, db.chatDao().action(id)!!.status)
        assertNotNull(repo.confirmEdited(id, meal(300.0)))
        assertEquals(1, db.trackingDao().observeMeals().first().size)
    }

    @Test fun typeChangeIsRejectedAndRolledBack() = runBlocking {
        val id = repo.actions.insert(meal(30.0), 1L)
        assertTrue(repo.confirmEdited(id, ActionPayload.AddWater(day, 250)) is ConfirmResult.Failed)
        assertEquals(PendingStatus.PENDING, db.chatDao().action(id)!!.status)
        assertEquals(0, db.trackingDao().observeWater().first().size)
    }
}

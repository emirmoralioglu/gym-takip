package com.emir.gym.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM plan_days ORDER BY weekday")
    abstract fun observeDays(): Flow<List<PlanDay>>

    @Query("SELECT * FROM plan_exercises ORDER BY weekday, position, id")
    abstract fun observeExercises(): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises WHERE weekday = :weekday ORDER BY position, id")
    abstract suspend fun exercisesOf(weekday: Int): List<PlanExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertDayRaw(day: PlanDay)

    @Insert
    protected abstract suspend fun insertRaw(ex: PlanExercise): Long

    @Query("SELECT * FROM plan_exercises WHERE id = :id")
    protected abstract suspend fun byId(id: Long): PlanExercise?

    @Query(
        "UPDATE plan_exercises SET weekday = :weekday, position = :position, name = :name, " +
            "muscleGroup = :muscleGroup, sets = :sets, targetReps = :targetReps, " +
            "restSeconds = :restSeconds, note = :note, posture = :posture WHERE id = :id"
    )
    protected abstract suspend fun updateRaw(
        id: Long, weekday: Int, position: Int, name: String, muscleGroup: String,
        sets: Int, targetReps: Int, restSeconds: Int, note: String, posture: Boolean,
    )

    @Query("DELETE FROM plan_exercises WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long)

    @Query("UPDATE plan_exercises SET position = :position WHERE id = :id")
    protected abstract suspend fun setPosition(id: Long, position: Int)

    private fun checkWeekday(weekday: Int) =
        require(weekday in 1..7) { "Hafta gunu 1..7 olmali: $weekday" }

    /** Gun icindeki sirayi 0..n-1 olarak sikistirir. */
    private suspend fun compact(weekday: Int) {
        exercisesOf(weekday).forEachIndexed { i, e -> if (e.position != i) setPosition(e.id, i) }
    }

    @Transaction
    open suspend fun upsertDay(day: PlanDay) {
        checkWeekday(day.weekday)
        upsertDayRaw(day)
    }

    /** Gunun sonuna ekler (verilen position yok sayilir). */
    @Transaction
    open suspend fun add(ex: PlanExercise): Long {
        checkWeekday(ex.weekday)
        val next = exercisesOf(ex.weekday).size
        return insertRaw(ex.copy(id = 0, position = next))
    }

    /**
     * Gunceller. position istemciden alinmaz: ayni gunde mevcut sira korunur, gun degisirse
     * yeni gunun sonuna gider; sonra iki gun da sikistirilir. Kayit yoksa bir sey yapmaz.
     */
    @Transaction
    open suspend fun update(ex: PlanExercise) {
        checkWeekday(ex.weekday)
        val old = byId(ex.id) ?: return
        val position = if (old.weekday == ex.weekday) old.position else exercisesOf(ex.weekday).size
        updateRaw(
            ex.id, ex.weekday, position, ex.name, ex.muscleGroup,
            ex.sets, ex.targetReps, ex.restSeconds, ex.note, ex.posture,
        )
        compact(ex.weekday)
        if (old.weekday != ex.weekday) compact(old.weekday)
    }

    /** Siler ve kalan hareketlerin sirasini sikistirir; gun, verilen nesneden degil DB satirindan alinir. */
    @Transaction
    open suspend fun remove(ex: PlanExercise) {
        val row = byId(ex.id) ?: return
        deleteById(row.id)
        compact(row.weekday)
    }

    /** orderedIds: o gunun hareket id'leri yeni sirayla. Tek islemde yazilir. */
    @Transaction
    open suspend fun reorder(weekday: Int, orderedIds: List<Long>) {
        val current = exercisesOf(weekday).map { it.id }
        require(orderedIds.size == current.size && orderedIds.toSet() == current.toSet()) {
            "Sira listesi gunle uyusmuyor"
        }
        orderedIds.forEachIndexed { i, id -> setPosition(id, i) }
    }
}

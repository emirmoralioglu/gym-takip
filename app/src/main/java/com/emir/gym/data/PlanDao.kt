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
    abstract suspend fun upsertDay(day: PlanDay)

    @Insert
    protected abstract suspend fun insertRaw(ex: PlanExercise): Long

    @Update
    abstract suspend fun update(ex: PlanExercise)

    @Query("DELETE FROM plan_exercises WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long)

    @Query("UPDATE plan_exercises SET position = :position WHERE id = :id")
    protected abstract suspend fun setPosition(id: Long, position: Int)

    /** Gunun sonuna ekler (verilen position yok sayilir). */
    @Transaction
    open suspend fun add(ex: PlanExercise): Long {
        val next = exercisesOf(ex.weekday).size
        return insertRaw(ex.copy(id = 0, position = next))
    }

    /** Siler ve kalan hareketlerin sirasini 0..n-1 olarak sikistirir. */
    @Transaction
    open suspend fun remove(ex: PlanExercise) {
        deleteById(ex.id)
        exercisesOf(ex.weekday).forEachIndexed { i, e -> if (e.position != i) setPosition(e.id, i) }
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

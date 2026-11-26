package com.emir.gym.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_entries ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<WorkoutEntry>>

    // id=0 ise yeni kayit, degilse ayni id uzerine yazar (duzenleme).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WorkoutEntry): Long

    @Delete
    suspend fun delete(entry: WorkoutEntry)
}

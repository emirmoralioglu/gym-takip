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

    // Liste tek islemde yazilir: ya hepsi ya hicbiri (kopyalama).
    @Insert
    suspend fun insertAll(entries: List<WorkoutEntry>)

    @Delete
    suspend fun delete(entry: WorkoutEntry)

    @Query("SELECT * FROM custom_exercises ORDER BY name")
    fun observeCustom(): Flow<List<CustomExercise>>

    // Ayni ad varsa yazmaz, -1 doner.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustom(exercise: CustomExercise): Long
}

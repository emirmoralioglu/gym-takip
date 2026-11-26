package com.emir.gym.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Query("SELECT * FROM activity_logs ORDER BY epochDay DESC, id DESC")
    fun observeActivities(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(log: ActivityLog): Long

    @Delete
    suspend fun deleteActivity(log: ActivityLog)

    @Query("SELECT * FROM meals ORDER BY epochDay DESC, id DESC")
    fun observeMeals(): Flow<List<Meal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeal(meal: Meal): Long

    @Delete
    suspend fun deleteMeal(meal: Meal)

    @Query("SELECT * FROM water_logs ORDER BY epochDay DESC, id DESC")
    fun observeWater(): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWater(log: WaterLog): Long

    @Delete
    suspend fun deleteWater(log: WaterLog)

    @Query("SELECT * FROM body_weights ORDER BY epochDay")
    fun observeBodyWeights(): Flow<List<BodyWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBodyWeight(w: BodyWeight)

    @Delete
    suspend fun deleteBodyWeight(w: BodyWeight)
}

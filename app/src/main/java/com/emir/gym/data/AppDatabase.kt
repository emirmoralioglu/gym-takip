package com.emir.gym.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorkoutEntry::class, CustomExercise::class,
        PlanDay::class, PlanExercise::class, ActivityLog::class,
        Meal::class, WaterLog::class, BodyWeight::class,
        ChatMessageEntity::class, PendingActionEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun planDao(): PlanDao
    abstract fun trackingDao(): TrackingDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Yeni kurulumda (tablolar yeni olusunca) varsayilan plani yazar. */
        val seedOnCreate = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = DefaultPlan.seed(db)
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "gym.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(seedOnCreate)
                .build().also { instance = it }
        }
    }
}

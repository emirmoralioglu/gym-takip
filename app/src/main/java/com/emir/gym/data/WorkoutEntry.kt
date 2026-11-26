package com.emir.gym.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tek bir egzersiz kaydi. Tarih, LocalDate.toEpochDay() olarak saklanir. */
@Entity(tableName = "workout_entries")
data class WorkoutEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double,
    val note: String = "",
)

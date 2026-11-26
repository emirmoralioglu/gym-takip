package com.emir.gym.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kullanicinin kalici olarak ekledigi egzersiz. */
@Entity(tableName = "custom_exercises")
data class CustomExercise(
    @PrimaryKey val name: String,
    val muscleGroup: String,
)

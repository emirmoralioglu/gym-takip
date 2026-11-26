package com.emir.gym.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActivityType { YUZME, SAUNA, BUHAR }

/** Yuzme/sauna/buhar kaydi. distanceM yalnizca yuzme icin, istege bagli. */
@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: ActivityType,
    val minutes: Int,
    val distanceM: Int? = null,
    val note: String = "",
)

enum class MealType(val label: String) {
    KAHVALTI("Kahvaltı"), OGLE("Öğle"), AKSAM("Akşam"), ARA("Ara öğün")
}

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val mealType: MealType,
    val description: String = "",
    val proteinG: Double = 0.0,
    val calories: Int = 0,
    val carbsG: Double? = null,
    val fatG: Double? = null,
)

@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val ml: Int,
)

/** Gunde tek tarti: ayni gune yeniden yazmak eskisini degistirir. */
@Entity(tableName = "body_weights")
data class BodyWeight(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)

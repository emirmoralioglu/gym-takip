package com.emir.gym.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlanDayType { AGIRLIK, YUZME, DINLENME }

/** Haftanin bir gunu: weekday 1=Pazartesi ... 7=Pazar (java.time.DayOfWeek.value). */
@Entity(tableName = "plan_days")
data class PlanDay(
    @PrimaryKey val weekday: Int,
    val title: String,
    val type: PlanDayType,
    val note: String = "",
)

/** Bir plan gunundeki hareket. position gun icinde 0'dan baslar. */
@Entity(tableName = "plan_exercises", indices = [Index("weekday")])
data class PlanExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int,
    val position: Int,
    val name: String,
    val muscleGroup: String,
    val sets: Int = 3,
    val targetReps: Int = 8,
    val restSeconds: Int = 90,
    val note: String = "",
    val posture: Boolean = false,
)

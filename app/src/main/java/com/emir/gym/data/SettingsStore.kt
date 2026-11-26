package com.emir.gym.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Yerel ayarlar (SharedPreferences). Kilo ve kalori hedefi varsayilan olarak bos. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _goals = MutableStateFlow(read())
    val goals: StateFlow<NutritionGoals> = _goals

    private fun read(): NutritionGoals {
        val d = NutritionGoals()
        return NutritionGoals(
            bodyWeightKg = prefs.getString(K_WEIGHT, null)?.toDoubleOrNull(),
            proteinPerKgMin = prefs.getString(K_PMIN, null)?.toDoubleOrNull() ?: d.proteinPerKgMin,
            proteinPerKgMax = prefs.getString(K_PMAX, null)?.toDoubleOrNull() ?: d.proteinPerKgMax,
            waterMl = prefs.getInt(K_WATER, d.waterMl),
            calories = if (prefs.contains(K_KCAL)) prefs.getInt(K_KCAL, 0) else null,
        )
    }

    /** Kilo (kg); null = temizle. Gecersizse (<=0, >500) false doner, kaydetmez. */
    fun setBodyWeight(kg: Double?): Boolean {
        if (kg != null && (kg <= 0.0 || kg > 500.0 || kg.isNaN())) return false
        prefs.edit().apply { if (kg == null) remove(K_WEIGHT) else putString(K_WEIGHT, kg.toString()) }.apply()
        _goals.value = read()
        return true
    }

    /** g/kg araligi; min>0 ve max>=min olmali. */
    fun setProteinRange(min: Double, max: Double): Boolean {
        if (min.isNaN() || max.isNaN() || min <= 0.0 || max < min || max > 10.0) return false
        prefs.edit().putString(K_PMIN, min.toString()).putString(K_PMAX, max.toString()).apply()
        _goals.value = read()
        return true
    }

    fun setWaterGoal(ml: Int): Boolean {
        if (ml <= 0 || ml > 20_000) return false
        prefs.edit().putInt(K_WATER, ml).apply()
        _goals.value = read()
        return true
    }

    /** Kalori hedefi; null = temizle. */
    fun setCalorieGoal(kcal: Int?): Boolean {
        if (kcal != null && (kcal <= 0 || kcal > 20_000)) return false
        prefs.edit().apply { if (kcal == null) remove(K_KCAL) else putInt(K_KCAL, kcal) }.apply()
        _goals.value = read()
        return true
    }

    private companion object {
        const val K_WEIGHT = "body_weight_kg"
        const val K_PMIN = "protein_per_kg_min"
        const val K_PMAX = "protein_per_kg_max"
        const val K_WATER = "water_goal_ml"
        const val K_KCAL = "calorie_goal"
    }
}

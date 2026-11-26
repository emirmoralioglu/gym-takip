package com.emir.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emir.gym.data.ActivityLog
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.BodyWeight
import com.emir.gym.data.CustomExercise
import com.emir.gym.data.Meal
import com.emir.gym.data.NewRecord
import com.emir.gym.data.NutritionGoals
import com.emir.gym.data.PlanDay
import com.emir.gym.data.PlanExercise
import com.emir.gym.data.Records
import com.emir.gym.data.RestTimer
import com.emir.gym.data.SettingsStore
import com.emir.gym.data.WaterLog
import com.emir.gym.data.WorkoutEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GymViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).workoutDao()
    private val prefs = app.getSharedPreferences("rest_timer", android.content.Context.MODE_PRIVATE)

    /** null = henuz yuklenmedi. */
    val entries: StateFlow<List<WorkoutEntry>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val customExercises: StateFlow<List<CustomExercise>> =
        dao.observeCustom().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val planDao = AppDatabase.get(app).planDao()
    private val trackingDao = AppDatabase.get(app).trackingDao()
    private val settingsStore = SettingsStore(app)

    private fun <T> Flow<T>.hot(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val planDays: StateFlow<List<PlanDay>> = planDao.observeDays().hot(emptyList())
    val planExercises: StateFlow<List<PlanExercise>> = planDao.observeExercises().hot(emptyList())
    val activities: StateFlow<List<ActivityLog>> = trackingDao.observeActivities().hot(emptyList())
    val meals: StateFlow<List<Meal>> = trackingDao.observeMeals().hot(emptyList())
    val waterLogs: StateFlow<List<WaterLog>> = trackingDao.observeWater().hot(emptyList())
    val bodyWeights: StateFlow<List<BodyWeight>> = trackingDao.observeBodyWeights().hot(emptyList())
    val goals: StateFlow<NutritionGoals> = settingsStore.goals

    /** Kullaniciya gosterilecek mesaj (hata ya da bilgi). */
    val error = MutableStateFlow<String?>(null)

    /** Kayit sonrasi kirilan rekor; kullanici kapatana kadar bekler. */
    val newRecord = MutableStateFlow<NewRecord?>(null)

    /** Dinlenme bitis zamani (System.currentTimeMillis); null = sayac yok. */
    val restEndAt = MutableStateFlow<Long?>(null)
    private var restJob: Job? = null

    init {
        // Surec olduyse sayac zaman damgasindan devam eder; suresi gectiyse temizlenir.
        val saved = prefs.getLong(KEY_END, 0L)
        if (saved > System.currentTimeMillis()) scheduleRest(saved) else prefs.edit().remove(KEY_END).apply()
    }

    fun save(entry: WorkoutEntry) {
        // Rekor kontrolu kayit oncesi liste ile; yalnizca yazma basarili olursa gosterilir.
        val before = entries.value
        viewModelScope.launch {
            try {
                dao.upsert(entry)
                if (before != null) newRecord.value = Records.check(before, entry)
            } catch (e: Exception) {
                error.value = "Kaydedilemedi: ${e.message ?: "bilinmeyen hata"}"
            }
        }
    }

    fun delete(entry: WorkoutEntry) = run("Silinemedi") { dao.delete(entry) }

    fun copyEntries(items: List<WorkoutEntry>) = run("Kopyalanamadı") {
        dao.insertAll(items)
        error.value = "${items.size} kayıt bugüne kopyalandı"
    }

    fun addCustomExercise(ex: CustomExercise) = run("Egzersiz eklenemedi") {
        if (dao.insertCustom(ex) == -1L) error.value = "Bu egzersiz zaten listede var"
    }

    fun addPlanExercise(ex: PlanExercise) = run("Plana eklenemedi") { planDao.add(ex) }
    fun updatePlanExercise(ex: PlanExercise) = run("Plan güncellenemedi") { planDao.update(ex) }
    fun removePlanExercise(ex: PlanExercise) = run("Plandan silinemedi") { planDao.remove(ex) }
    fun reorderPlan(weekday: Int, orderedIds: List<Long>) = run("Sıra değiştirilemedi") { planDao.reorder(weekday, orderedIds) }
    fun updatePlanDay(day: PlanDay) = run("Gün güncellenemedi") { planDao.upsertDay(day) }

    fun saveActivity(log: ActivityLog) = run("Aktivite kaydedilemedi") { trackingDao.upsertActivity(log) }
    fun deleteActivity(log: ActivityLog) = run("Aktivite silinemedi") { trackingDao.deleteActivity(log) }
    fun saveMeal(meal: Meal) = run("Öğün kaydedilemedi") { trackingDao.upsertMeal(meal) }
    fun deleteMeal(meal: Meal) = run("Öğün silinemedi") { trackingDao.deleteMeal(meal) }
    fun saveWater(log: WaterLog) = run("Su kaydedilemedi") { trackingDao.upsertWater(log) }
    fun deleteWater(log: WaterLog) = run("Su silinemedi") { trackingDao.deleteWater(log) }
    fun saveBodyWeight(w: BodyWeight) = run("Kilo kaydedilemedi") { trackingDao.upsertBodyWeight(w) }
    fun deleteBodyWeight(w: BodyWeight) = run("Kilo silinemedi") { trackingDao.deleteBodyWeight(w) }

    /** Ayar yazimlari gecersiz degerde false doner ve mesaj verir. */
    fun setBodyWeightGoal(kg: Double?) = report(settingsStore.setBodyWeight(kg), "Geçersiz kilo")
    fun setProteinRange(min: Double, max: Double) = report(settingsStore.setProteinRange(min, max), "Geçersiz protein aralığı")
    fun setWaterGoal(ml: Int) = report(settingsStore.setWaterGoal(ml), "Geçersiz su hedefi")
    fun setCalorieGoal(kcal: Int?) = report(settingsStore.setCalorieGoal(kcal), "Geçersiz kalori hedefi")

    private fun report(ok: Boolean, msg: String): Boolean { if (!ok) error.value = msg; return ok }

    fun notify(msg: String) { error.value = msg }
    fun clearError() { error.value = null }
    fun clearNewRecord() { newRecord.value = null }

    fun startRest(seconds: Int) {
        val end = System.currentTimeMillis() + seconds * 1000L
        prefs.edit().putLong(KEY_END, end).apply()
        scheduleRest(end)
    }

    fun cancelRest() {
        restJob?.cancel()
        restEndAt.value = null
        prefs.edit().remove(KEY_END).apply()
    }

    private fun scheduleRest(end: Long) {
        restJob?.cancel()
        restEndAt.value = end
        restJob = viewModelScope.launch {
            // Sistem uykudayken gecikme uzayabilir; bitis her seferinde saatle yeniden olculur.
            while (System.currentTimeMillis() < end) delay(maxOf(50L, minOf(1000L, end - System.currentTimeMillis())))
            RestTimer.vibrate(getApplication())
            restEndAt.value = null
            prefs.edit().remove(KEY_END).apply()
            error.value = "Dinlenme bitti"
        }
    }

    private fun run(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { error.value = "$msg: ${e.message ?: "bilinmeyen hata"}" }
        }
    }

    private companion object { const val KEY_END = "end_at" }
}

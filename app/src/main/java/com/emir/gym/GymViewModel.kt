package com.emir.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.CustomExercise
import com.emir.gym.data.NewRecord
import com.emir.gym.data.Records
import com.emir.gym.data.RestTimer
import com.emir.gym.data.WorkoutEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

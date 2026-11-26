package com.emir.gym

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.WorkoutEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GymViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).workoutDao()

    /** null = henuz yuklenmedi. */
    val entries: StateFlow<List<WorkoutEntry>?> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Kullaniciya gosterilecek hata mesaji (kayit/silme basarisizsa). */
    val error = MutableStateFlow<String?>(null)

    fun save(entry: WorkoutEntry) = run("Kaydedilemedi") { dao.upsert(entry) }
    fun delete(entry: WorkoutEntry) = run("Silinemedi") { dao.delete(entry) }
    fun clearError() { error.value = null }

    private fun run(msg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { error.value = "$msg: ${e.message ?: "bilinmeyen hata"}" }
        }
    }
}

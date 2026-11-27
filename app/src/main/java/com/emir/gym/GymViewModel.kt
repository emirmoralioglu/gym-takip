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
import com.emir.gym.data.FormResult
import com.emir.gym.data.Records
import com.emir.gym.data.RestTimer
import com.emir.gym.data.SettingsStore
import com.emir.gym.data.WaterLog
import com.emir.gym.data.WorkoutEntry
import com.emir.gym.ui.FolderLogic
import com.emir.gym.ui.RowKey
import com.emir.gym.ui.RowState
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /** Sayacin baslangic suresi (sn); halka orani icin. */
    val restTotalSeconds = MutableStateFlow(0)

    /** Duraklatildiysa kalan ms; calisirken null. restEndAt de duraklatinca null olur. */
    val restPausedLeftMs = MutableStateFlow<Long?>(null)

    init {
        // Surec olduyse sayac zaman damgasindan devam eder; suresi gectiyse temizlenir.
        val saved = prefs.getLong(KEY_END, 0L)
        if (saved > System.currentTimeMillis()) {
            restTotalSeconds.value = ((saved - System.currentTimeMillis() + 999) / 1000).toInt()
            scheduleRest(saved)
        } else prefs.edit().remove(KEY_END).apply()
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

    /** Gecmis ekranindan silme; bagli bir set satiri varsa baglantisi da kaldirilir. */
    fun delete(entry: WorkoutEntry) = run("Silinemedi") {
        deletedIds += entry.id
        val pending = setRows.value.filterValues { it.entryId == entry.id }.keys.mapNotNull { editJobs.remove(it) }
        try {
            // Bekleyen duzenlemeler bitmeden silinmez; silinen kayit geri yazilamaz.
            pending.forEach { it.cancelAndJoin() }
            dao.delete(entry)
        } catch (e: Exception) {
            deletedIds -= entry.id
            throw e
        }
        setRows.update { m -> m.mapValues { (_, st) -> if (st.entryId == entry.id) st.copy(entryId = null) else st } }
    }

    fun copyEntries(items: List<WorkoutEntry>) = run("Kopyalanamadı") {
        dao.insertAll(items)
        error.value = "${items.size} kayıt bugüne kopyalandı"
    }

    // ---- Gun klasoru: set satirlari <-> workout_entries ----

    /** Set satirlari (yalnizca acik klasorun tarihi). Kayit eslemesi burada tutulur. */
    val setRows = MutableStateFlow<Map<RowKey, RowState>>(emptyMap())
    private val pendingRows = mutableSetOf<RowKey>()
    private val editJobs = mutableMapOf<RowKey, Job>()

    /** Klasor acilinca / plan degisince: o tarihin mevcut kayitlarindan satirlari yeniden kurar. */
    fun syncRows(date: LocalDate, plan: List<PlanExercise>) {
        lastSync = date to plan
        // Yazma surerken liste henuz guncel olmayabilir; bitince yeniden kurulur.
        if (pendingRows.isNotEmpty()) return
        val all = entries.value ?: return
        setRows.update { FolderLogic.reconcile(it, plan, date.toEpochDay(), all, deletedIds) }
    }

    private var lastSync: Pair<LocalDate, List<PlanExercise>>? = null

    /** Silinen kayit id'leri: akis henuz guncellenmediyse bile yeniden bir satira baglanmaz. */
    private val deletedIds = mutableSetOf<Long>()

    fun addExtraSet(plan: PlanExercise, epochDay: Long) {
        setRows.update { m ->
            val next = (m.keys.filter { it.planId == plan.id && it.epochDay == epochDay }.maxOfOrNull { it.index } ?: -1) + 1
            m + (RowKey(plan.id, epochDay, next) to RowState("", plan.targetReps.toString()))
        }
    }

    /** Set satirinin metnini degistirir; set tamamsa kisa bir bekleyisten sonra kaydi gunceller. */
    fun editRow(key: RowKey, plan: PlanExercise, kg: String, reps: String) {
        val row = setRows.value[key] ?: return
        val next = row.copy(kg = kg, reps = reps)
        setRows.update { it + (key to next) }
        val id = row.entryId ?: return
        editJobs[key]?.cancel()
        editJobs[key] = viewModelScope.launch {
            delay(EDIT_DEBOUNCE_MS)
            try {
                // Bekleme sirasinda geri alindi/silindiyse ya da satirin baglantisi degistiyse hicbir sey yazilmaz.
                if (id in deletedIds || setRows.value[key]?.entryId != id) return@launch
                val old = entries.value?.firstOrNull { it.id == id }
                when (val r = FolderLogic.editedEntry(old, key.epochDay, plan, next)) {
                    is FormResult.Error -> {
                        error.value = r.message
                        // Gecersiz metin satirda kalmasin: kayittaki degere geri don.
                        if (old != null && FolderLogic.isBoundTo(old, key.epochDay, plan)) {
                            setRows.update { m ->
                                if (m[key]?.entryId == id) m + (key to RowState(FolderLogic.kgText(old.weightKg), old.reps.toString(), id)) else m
                            }
                        }
                    }
                    is FormResult.Ok -> {
                        val e = r.entry
                        val before = entries.value
                        dao.upsert(e)
                        // Ayni bitmis deger icin rekor bir kez bildirilir.
                        if (before != null && notifiedRecordFor[id] != e.weightKg to e.reps) {
                            val rec = Records.check(before, e)
                            if (rec != null) { newRecord.value = rec; notifiedRecordFor[id] = e.weightKg to e.reps }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Kaydedilemedi: ${e.message ?: "bilinmeyen hata"}"
            }
        }
    }

    private val notifiedRecordFor = mutableMapOf<Long, Pair<Double, Int>>()

    /** Seti isaretler (kayit yazar, dinlenme sayacini baslatir) ya da geri alir (kaydi siler). */
    fun toggleSet(key: RowKey, plan: PlanExercise, done: Boolean) {
        val row = setRows.value[key] ?: return
        if (key in pendingRows) return
        if (done) {
            if (row.done) return
            val hint = entries.value?.let { FolderLogic.lastKg(plan.name, it, key.epochDay) }
            val r = FolderLogic.toEntry(0, key.epochDay, plan, row, hint)
            if (r is FormResult.Error) { error.value = r.message; return }
            val entry = (r as FormResult.Ok).entry
            val before = entries.value
            pendingRows += key
            viewModelScope.launch {
                try {
                    val id = dao.upsert(entry)
                    setRows.update { m ->
                        if (key in m) m + (key to RowState(FolderLogic.kgText(entry.weightKg), entry.reps.toString(), id)) else m
                    }
                    if (before != null) {
                        newRecord.value = Records.check(before, entry)
                        if (newRecord.value != null) notifiedRecordFor[id] = entry.weightKg to entry.reps
                    }
                    startRest(plan.restSeconds)
                } catch (e: Exception) {
                    error.value = "Kaydedilemedi: ${e.message ?: "bilinmeyen hata"}"
                } finally {
                    pendingRows -= key
                    lastSync?.let { (d, p) -> syncRows(d, p) }
                }
            }
        } else {
            val id = row.entryId ?: return
            val entry = entries.value?.firstOrNull { it.id == id }
            if (entry == null) { error.value = "Kayıt henüz yüklenmedi; tekrar dene"; return }
            if (!FolderLogic.isBoundTo(entry, key.epochDay, plan)) {
                // Kayit artik tek setlik degil (Gecmis'ten duzenlenmis): asla silinmez, yalnizca baglanti kopar.
                editJobs.remove(key)?.cancel()
                setRows.update { m -> m[key]?.let { m + (key to it.copy(entryId = null)) } ?: m }
                error.value = "Bu kayıt artık tek set değil; silinmedi. Geçmiş bölümünden düzenle"
                return
            }
            val pendingEdit = editJobs.remove(key)
            deletedIds += id
            pendingRows += key
            viewModelScope.launch {
                try {
                    // Bekleyen duzenleme bitmeden silinmez; silinen kayit yeniden yazilamaz.
                    pendingEdit?.cancelAndJoin()
                    dao.delete(entry)
                    setRows.update { m -> m[key]?.let { m + (key to it.copy(entryId = null)) } ?: m }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    deletedIds -= id
                    throw e
                } catch (e: Exception) {
                    deletedIds -= id
                    error.value = "Silinemedi: ${e.message ?: "bilinmeyen hata"}"
                } finally {
                    pendingRows -= key
                }
            }
        }
    }

    /** Onceki gunun degerlerini tamamlanmamis satirlara yazar (kayit yazmaz). Doner: degisen satir sayisi. */
    fun fillRows(plan: List<PlanExercise>, source: LocalDate): Int {
        val all = entries.value ?: return 0
        val src = all.filter { it.epochDay == source.toEpochDay() }
        val (rows, changed) = FolderLogic.fillFromSource(setRows.value, plan, src)
        setRows.value = rows
        return changed
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
    /** Ogunu yazar; yalnizca yazma basarili olursa [onSaved] cagrilir (hatada mesaj verilir). */
    fun saveMealThen(meal: Meal, onSaved: () -> Unit) {
        viewModelScope.launch {
            val ok = try { trackingDao.upsertMeal(meal); true } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                error.value = "Öğün kaydedilemedi: ${e.message ?: "bilinmeyen hata"}"; false
            }
            if (ok) onSaved()
        }
    }

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
        restPausedLeftMs.value = null
        restTotalSeconds.value = seconds
        val end = System.currentTimeMillis() + seconds * 1000L
        prefs.edit().putLong(KEY_END, end).apply()
        scheduleRest(end)
    }

    fun cancelRest() {
        restJob?.cancel()
        restEndAt.value = null
        restPausedLeftMs.value = null
        prefs.edit().remove(KEY_END).apply()
    }

    fun pauseRest() {
        val end = restEndAt.value ?: return
        restPausedLeftMs.value = maxOf(0L, end - System.currentTimeMillis())
        restJob?.cancel()
        restEndAt.value = null
        prefs.edit().remove(KEY_END).apply()
    }

    fun resumeRest() {
        val left = restPausedLeftMs.value ?: return
        restPausedLeftMs.value = null
        val end = System.currentTimeMillis() + left
        prefs.edit().putLong(KEY_END, end).apply()
        scheduleRest(end)
    }

    /** Calisan ya da duraklatilmis sayaca saniye ekler. */
    fun addRest(seconds: Int) {
        restPausedLeftMs.value?.let {
            restPausedLeftMs.value = it + seconds * 1000L
            restTotalSeconds.value += seconds
            return
        }
        val end = restEndAt.value ?: return
        restTotalSeconds.value += seconds
        val newEnd = end + seconds * 1000L
        prefs.edit().putLong(KEY_END, newEnd).apply()
        scheduleRest(newEnd)
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

    private companion object {
        const val KEY_END = "end_at"
        const val EDIT_DEBOUNCE_MS = 700L
    }
}

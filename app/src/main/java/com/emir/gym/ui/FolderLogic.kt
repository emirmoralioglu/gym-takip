package com.emir.gym.ui

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.data.EntryForm
import com.emir.gym.data.ExerciseCatalog
import com.emir.gym.data.FormResult
import com.emir.gym.data.PlanDay
import com.emir.gym.data.PlanDayType
import com.emir.gym.data.PlanExercise
import com.emir.gym.data.PlanLogic
import com.emir.gym.data.WeeklyStats
import com.emir.gym.data.WorkoutEntry
import com.emir.gym.ui.components.UiFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Bir set satirinin kimligi: plan hareketi + tarih + hareket icindeki sira (0'dan). */
data class RowKey(val planId: Long, val epochDay: Long, val index: Int)

/** entryId doluysa set tamamlanmistir ve workout_entries'teki o satira baglidir. */
data class RowState(val kg: String, val reps: String, val entryId: Long? = null) {
    val done: Boolean get() = entryId != null
}

/** Bir gune ait hareket ozeti: "Bench press" -> "3×8 @ 60". */
data class ExerciseLine(val name: String, val text: String) {
    val display: String get() = "$name $text"
}

/** Gun klasoru icin saf mantik (arayuzden bagimsiz, birim testli). */
object FolderLogic {
    /** Bir set icin tahmin edilen calisma suresi (sn); sure tahmininde dinlenmeye eklenir. */
    const val SET_WORK_SECONDS = 40

    /** Hafta gununun (1..7) bugunden once ya da bugun olan en yakin tarihi. */
    fun nearestDate(weekday: Int, today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekday)))

    /** Tarih secici: yalnizca o hafta gunu ve gelecekte olmayan tarihler. */
    fun isSelectable(date: LocalDate, weekday: Int, today: LocalDate): Boolean =
        date.dayOfWeek.value == weekday && !date.isAfter(today)

    /**
     * Tahmini sure (dk). Agirlik: set*(dinlenme+calisma) toplami, 5 dk'ya yukari yuvarlanir.
     * Yuzme: gun notundaki "~45 dk" ifadesi. Bilinmiyorsa null (uydurma sure yok).
     */
    fun estimateMinutes(day: PlanDay?, exercises: List<PlanExercise>): Int? = when (day?.type) {
        PlanDayType.AGIRLIK -> {
            val sec = exercises.sumOf { it.sets.toLong() * (it.restSeconds + SET_WORK_SECONDS) }
            if (sec <= 0L) null else (ceil(sec / 60.0 / 5.0) * 5).toInt()
        }
        PlanDayType.YUZME -> Regex("~\\s*(\\d+)\\s*dk").find(day.note)?.groupValues?.get(1)?.toIntOrNull()
        else -> null
    }

    /** Alanlara yazilacak kg metni: tam sayiysa ondaliksiz, nokta ondalik, binlik ayraci yok. */
    fun kgText(kg: Double): String {
        if (kg % 1.0 == 0.0) return kg.toLong().toString()
        return "%.2f".format(java.util.Locale.US, kg).trimEnd('0').trimEnd('.')
    }

    private fun sameExercise(e: WorkoutEntry, key: String) = ExerciseCatalog.key(e.exercise) == key

    /** Son kullanilan agirlik (hint); 0 ya da yoksa null. */
    fun lastKg(name: String, entries: List<WorkoutEntry>): Double? =
        PlanLogic.lastEntry(name, entries)?.weightKg?.takeIf { it > 0.0 }

    /**
     * Set satirlarini o tarihin kayitlarindan yeniden kurar.
     * - Mevcut satirlar korunur (yazilmakta olan metin bozulmaz); bagli kaydin degeri metinden farkliysa metin kayittan alinir.
     * - Bagli kaydi listede gorunmeyen satir tamamlanmis kalir (yazma henuz akisa yansimamis olabilir);
     *   silme/geri alma yollari baglantiyi acikca kaldirir.
     * - Hicbir satira bagli olmayan tek setlik kayitlar bos satirlara (yoksa yeni satirlara) atanir.
     * - Birden cok setlik (eski/toplu) kayitlar satir uretmez; plan set sayisindan dusulur.
     * Yalnizca [epochDay]'e ait satirlar doner.
     */
    fun reconcile(
        current: Map<RowKey, RowState>,
        plan: List<PlanExercise>,
        epochDay: Long,
        entries: List<WorkoutEntry>,
        ignoreIds: Set<Long> = emptySet(),
    ): Map<RowKey, RowState> {
        val out = LinkedHashMap<RowKey, RowState>()
        for (pe in plan) {
            val k = ExerciseCatalog.key(pe.name)
            val day = entries.filter { it.epochDay == epochDay && sameExercise(it, k) }
            val singles = day.filter { it.sets == 1 }.sortedBy { it.id }
            val bulkSets = day.filter { it.sets > 1 }.sumOf { it.sets }
            val existing = current.filterKeys { it.planId == pe.id && it.epochDay == epochDay }
            val rows = sortedMapOf<Int, RowState>()
            existing.forEach { (key, st) ->
                val entry = st.entryId?.let { id -> singles.firstOrNull { it.id == id } }
                rows[key.index] = if (entry != null && !matches(st, entry)) {
                    st.copy(kg = kgText(entry.weightKg), reps = entry.reps.toString())
                } else st
            }
            val claimed = rows.values.mapNotNull { it.entryId }.toSet()
            val unclaimed = singles.filter { it.id !in claimed && it.id !in ignoreIds }
            var n = maxOf(pe.sets - bulkSets, (rows.keys.maxOrNull() ?: -1) + 1, 0)
            val queue = ArrayDeque(unclaimed)
            var i = 0
            while (queue.isNotEmpty()) {
                if (i >= n) n = i + 1
                if (rows[i]?.entryId == null) {
                    val e = queue.removeFirst()
                    rows[i] = RowState(kgText(e.weightKg), e.reps.toString(), e.id)
                }
                i++
            }
            for (idx in 0 until n) {
                out[RowKey(pe.id, epochDay, idx)] = rows[idx] ?: RowState("", pe.targetReps.toString())
            }
        }
        return out
    }

    private fun matches(st: RowState, e: WorkoutEntry): Boolean {
        val kg = st.kg.replace(',', '.').toDoubleOrNull() ?: 0.0
        return kg == e.weightKg && st.reps.toIntOrNull() == e.reps
    }

    /**
     * Set satirini kayda cevirir (sets=1). kg bos ise [hintKg] kullanilir, o da yoksa 0 (agirliksiz hareket).
     * Dogrulama EntryForm ile yapilir; hata Turkce mesajla doner.
     */
    fun toEntry(
        id: Long, epochDay: Long, plan: PlanExercise, row: RowState, hintKg: Double?, note: String = "",
    ): FormResult {
        val kgText = row.kg.trim().ifEmpty { hintKg?.let(::kgText) ?: "0" }
        return EntryForm.parse(id, epochDay, plan.name, "1", row.reps, kgText, note, plan.muscleGroup)
    }

    /** Kaynak tarihin kayitlarindan, tamamlanmamis satirlara kg/tekrar doldurur. Doner: yeni satirlar, degisen satir sayisi. */
    fun fillFromSource(
        rows: Map<RowKey, RowState>,
        plan: List<PlanExercise>,
        sourceEntries: List<WorkoutEntry>,
    ): Pair<Map<RowKey, RowState>, Int> {
        val out = LinkedHashMap(rows)
        var changed = 0
        for (pe in plan) {
            val k = ExerciseCatalog.key(pe.name)
            val src = sourceEntries.filter { sameExercise(it, k) }.sortedBy { it.id }
                .flatMap { e -> List(e.sets.coerceAtLeast(1)) { e } }
            if (src.isEmpty()) continue
            rows.keys.filter { it.planId == pe.id }.sortedBy { it.index }.forEach { key ->
                val st = rows.getValue(key)
                if (st.done) return@forEach
                val s = src[minOf(key.index, src.lastIndex)]
                val new = st.copy(kg = kgText(s.weightKg), reps = s.reps.toString())
                if (new != st) { out[key] = new; changed++ }
            }
        }
        return out to changed
    }

    /** Doldurma bir seyin uzerine yazacak mi (tamamlanmamis satirda kg zaten girilmis)? */
    fun fillWouldOverwrite(rows: Map<RowKey, RowState>): Boolean =
        rows.values.any { !it.done && it.kg.isNotBlank() }

    /** Klasorun bu tarihten onceki en yakin ayni-hafta-gunu kayit tarihi; yoksa null. */
    fun lastSourceDate(weekday: Int, date: LocalDate, entries: List<WorkoutEntry>): LocalDate? =
        PlanLogic.weekdayHistory(weekday, entries).firstOrNull { it.date.isBefore(date) }?.date

    /** Gun kayitlarini hareket basina ozetler: ayni (tekrar, kg) setleri toplanir, "3×8 @ 60". */
    fun lines(entries: List<WorkoutEntry>): List<ExerciseLine> =
        entries.sortedBy { it.id }.groupBy { ExerciseCatalog.key(it.exercise) }.values.map { list ->
            val segs = LinkedHashMap<Pair<Int, Double>, Int>()
            list.forEach { e -> segs.merge(e.reps to e.weightKg, e.sets) { a, b -> a + b } }
            val text = segs.entries.joinToString(" · ") { (rk, sets) ->
                val base = "$sets×${rk.first}"
                if (rk.second > 0.0) "$base @ ${UiFormat.number(rk.second)}" else base
            }
            ExerciseLine(ExerciseCatalog.displayName(list.first().exercise), text)
        }

    /** Toplam tonaj (kg). */
    fun tonnage(entries: List<WorkoutEntry>): Double = entries.sumOf { WeeklyStats.volume(it) }

    /** Bir hafta gunune dusen tarihlerin aktivite kayitlari; en yeni tarih basta. */
    fun activityHistory(weekday: Int, activities: List<ActivityLog>): List<Pair<LocalDate, List<ActivityLog>>> =
        activities.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek.value == weekday }
            .groupBy { it.epochDay }
            .toSortedMap(compareByDescending { it })
            .map { (d, l) -> LocalDate.ofEpochDay(d) to l.sortedBy { it.id } }

    /** weekStart'tan baslayan 7 gunde tur basina toplam dakika. */
    fun activityMinutes(weekStart: LocalDate, activities: List<ActivityLog>): Map<ActivityType, Int> {
        val from = weekStart.toEpochDay()
        val inWeek = activities.filter { it.epochDay in from..(from + 6) }
        return ActivityType.entries.associateWith { t -> inWeek.filter { it.type == t }.sumOf { it.minutes } }
    }

    /** Onceki haftaya gore yuzde degisim; onceki 0 ise null (kiyas yok). */
    fun deltaPercent(current: Double, previous: Double): Int? =
        if (previous <= 0.0) null else ((current - previous) / previous * 100.0).roundToInt()
}

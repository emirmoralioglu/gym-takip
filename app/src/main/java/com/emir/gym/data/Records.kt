package com.emir.gym.data

data class ExerciseRecord(
    val exercise: String,
    val maxWeightKg: Double,
    val maxWeightDay: Long,
    val best1Rm: Double?,
    val best1RmDay: Long?,
)

/** Yeni kayit onceki rekorlari astiysa doner. prev* = onceki en iyi deger. */
data class NewRecord(
    val exercise: String,
    val weightBroken: Boolean,
    val oneRmBroken: Boolean,
    val weightKg: Double,
    val oneRm: Double,
    val prevWeightKg: Double,
    val prevOneRm: Double,
)

object Records {
    private const val EPS = 1e-9

    /** Tahmini 1RM yalniz 1..12 tekrar icin guvenilir; fazlasinda sisiyor. */
    const val MAX_1RM_REPS = 12

    /** Epley: kg * (1 + tekrar/30). Tek tekrarda kaldirilan agirlik 1RM'dir. 12'den fazla tekrarda null. */
    fun epley1Rm(weightKg: Double, reps: Int): Double? = when {
        reps < 1 || reps > MAX_1RM_REPS -> null
        reps == 1 -> weightKg
        else -> weightKg * (1.0 + reps / 30.0)
    }

    fun oneRm(e: WorkoutEntry): Double? = epley1Rm(e.weightKg, e.reps)

    /** Egzersiz basina rekorlar, ada gore sirali. Agirligi 0 olan kayitlar sayilmaz. */
    fun compute(entries: List<WorkoutEntry>): List<ExerciseRecord> =
        entries.filter { it.weightKg > 0 }
            .groupBy { ExerciseCatalog.key(it.exercise) }
            .map { (_, list) ->
                // Esitlikte ilk ulasilan (en eski) gun korunur.
                val byDay = list.sortedBy { it.epochDay }
                val w = byDay.reduce { a, b -> if (b.weightKg > a.weightKg + EPS) b else a }
                val r = byDay.filter { oneRm(it) != null }
                    .reduceOrNull { a, b -> if (oneRm(b)!! > oneRm(a)!! + EPS) b else a }
                ExerciseRecord(w.exercise.trim(), w.weightKg, w.epochDay, r?.let { oneRm(it) }, r?.epochDay)
            }
            .sortedBy { ExerciseCatalog.key(it.exercise) }

    /**
     * [existing] kaydedilmeden onceki kayitlar. Duzenlemede ayni id'li eski satir hesaba katilmaz.
     * Egzersizin daha once hic agirlikli kaydi yoksa bu bir "kirma" degil ilk deger sayilir (null).
     */
    fun check(existing: List<WorkoutEntry>, e: WorkoutEntry): NewRecord? {
        if (e.weightKg <= 0) return null
        val k = ExerciseCatalog.key(e.exercise)
        val prior = existing.filter {
            ExerciseCatalog.key(it.exercise) == k && it.weightKg > 0 && (e.id == 0L || it.id != e.id)
        }
        if (prior.isEmpty()) return null
        val prevW = prior.maxOf { it.weightKg }
        // 1RM yalniz 1..12 tekrarlik satirlardan; hic yoksa 1RM rekoru sayilmaz.
        val prevR = prior.mapNotNull { oneRm(it) }.maxOrNull()
        val r = oneRm(e)
        val wBroken = e.weightKg > prevW + EPS
        val rBroken = r != null && prevR != null && r > prevR + EPS
        if (!wBroken && !rBroken) return null
        return NewRecord(e.exercise.trim(), wBroken, rBroken, e.weightKg, r ?: 0.0, prevW, prevR ?: 0.0)
    }
}

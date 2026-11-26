package com.emir.gym.data

import java.util.Locale

enum class MuscleGroup(val label: String) {
    GOGUS("Göğüs"),
    SIRT("Sırt"),
    OMUZ("Omuz"),
    KOL("Kol"),
    BACAK("Bacak"),
    KARIN("Karın"),
    KARDIYO("Kardiyo");

    companion object {
        fun fromKey(key: String): MuscleGroup? = entries.firstOrNull { it.name == key }
        fun labelOf(key: String): String = fromKey(key)?.label ?: ""
    }
}

data class ExerciseDef(val name: String, val group: MuscleGroup)

object ExerciseCatalog {
    /**
     * Ad karsilastirmasi icin anahtar: bosluk ve buyuk/kucuk harf farki yok sayilir; eski Turkce adlar yeni adla ayni anahtari verir.
     * I/i/İ/ı tek harf sayilir (Turkce klavyede "BENCH" ile "bench" ayni egzersizdir).
     */
    fun key(name: String): String {
        val k = fold(name)
        return LEGACY_KEYS[k] ?: k
    }

    private fun fold(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace("i\u0307", "i").replace('\u0131', 'i')

    /** Eski Turkce katalog adlari (katlanmis) -> yeni Ingilizce adin anahtari. Geriye uyumluluk. */
    private val LEGACY_KEYS: Map<String, String> =
        LEGACY_EXERCISE_RENAMES.entries.associate { (old, new) -> fold(old) to fold(new) }

    private fun g(group: MuscleGroup, vararg names: String) = names.map { ExerciseDef(it, group) }

    val all: List<ExerciseDef> =
        g(
            MuscleGroup.GOGUS,
            "Bench press", "Incline bench press", "Dumbbell bench press", "Incline dumbbell press",
            "Dumbbell fly", "Cable crossover", "Push-up", "Parallel bar dips",
            "Chest press machine", "Incline chest press machine",
        ) + g(
            MuscleGroup.SIRT,
            "Deadlift", "Lat pulldown", "Pull-up", "Barbell row", "One-arm dumbbell row",
            "Seated cable row", "T-bar row", "Back extension",
        ) + g(
            MuscleGroup.OMUZ,
            "Overhead press", "Dumbbell shoulder press", "Lateral raise", "Front raise",
            "Rear delt fly", "Face pull", "Shrug",
            "Shoulder press machine", "Reverse pec deck",
        ) + g(
            MuscleGroup.KOL,
            "Barbell curl", "Dumbbell curl", "Hammer curl", "Preacher curl",
            "Triceps pushdown", "Skull crusher", "Overhead triceps extension", "Bench dips",
        ) + g(
            MuscleGroup.BACAK,
            "Squat", "Front squat", "Leg press", "Romanian deadlift", "Lunge",
            "Bulgarian split squat", "Leg extension", "Leg curl", "Calf raise", "Hip thrust",
            "Hack squat", "Hip abduction",
        ) + g(
            MuscleGroup.KARIN,
            "Crunch", "Plank", "Leg raise", "Reverse crunch", "Cable crunch",
            "Russian twist", "Ab wheel",
        ) + g(
            MuscleGroup.KARDIYO,
            "Treadmill", "Cycling", "Elliptical", "Rowing machine", "Jump rope", "Walking",
        )

    fun byGroup(): Map<MuscleGroup, List<ExerciseDef>> = all.groupBy { it.group }

    /** Kayit icin kas grubu: once kayitta saklanan, yoksa katalog/ozel listeden. */
    fun groupOf(entry: WorkoutEntry, custom: List<CustomExercise>): MuscleGroup? =
        MuscleGroup.fromKey(entry.muscleGroup) ?: groupOfName(entry.exercise, custom)

    fun groupOfName(name: String, custom: List<CustomExercise>): MuscleGroup? {
        val k = key(name)
        all.firstOrNull { key(it.name) == k }?.let { return it.group }
        return custom.firstOrNull { key(it.name) == k }?.let { MuscleGroup.fromKey(it.muscleGroup) }
    }

    const val MAX_NAME_LENGTH = 60

    /** Ozel egzersiz adi dogrulama. Basarisizsa Turkce hata mesaji, basariliysa null. */
    fun validateCustom(name: String, group: MuscleGroup?, custom: List<CustomExercise>): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Egzersiz adı boş olamaz"
        if (n.length > MAX_NAME_LENGTH) return "Egzersiz adı en fazla $MAX_NAME_LENGTH karakter olabilir"
        if (group == null) return "Kas grubu seç"
        val k = key(n)
        if (all.any { key(it.name) == k } || custom.any { key(it.name) == k }) {
            return "Bu egzersiz zaten listede var"
        }
        return null
    }
}

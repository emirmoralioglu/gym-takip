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
     * Ad karsilastirmasi icin anahtar: bosluk ve buyuk/kucuk harf farki yok sayilir.
     * I/i/İ/ı tek harf sayilir (Turkce klavyede "BENCH" ile "bench" ayni egzersizdir).
     */
    fun key(name: String): String =
        name.trim().lowercase(Locale.ROOT).replace("i\u0307", "i").replace('\u0131', 'i')

    private fun g(group: MuscleGroup, vararg names: String) = names.map { ExerciseDef(it, group) }

    val all: List<ExerciseDef> =
        g(
            MuscleGroup.GOGUS,
            "Bench press", "Eğimli bench press", "Dambıl göğüs press", "Eğimli dambıl press",
            "Dambıl fly", "Kablo crossover", "Şınav", "Paralel bar dips",
        ) + g(
            MuscleGroup.SIRT,
            "Deadlift", "Lat pulldown", "Barfiks", "Barbell row", "Tek kol dambıl row",
            "Oturarak kablo row", "T-bar row", "Sırt ekstansiyonu",
        ) + g(
            MuscleGroup.OMUZ,
            "Overhead press", "Dambıl omuz press", "Yan lateral raise", "Ön raise",
            "Arka omuz fly", "Face pull", "Omuz silkme (shrug)",
        ) + g(
            MuscleGroup.KOL,
            "Barbell curl", "Dambıl curl", "Çekiç curl", "Preacher curl",
            "Triceps pushdown", "Skull crusher", "French press", "Bank dips",
        ) + g(
            MuscleGroup.BACAK,
            "Squat", "Ön squat", "Leg press", "Romanian deadlift", "Hamle (lunge)",
            "Bulgarian split squat", "Leg extension", "Leg curl", "Baldır kaldırma", "Hip thrust",
        ) + g(
            MuscleGroup.KARIN,
            "Crunch", "Plank", "Bacak kaldırma", "Ters crunch", "Kablo crunch",
            "Russian twist", "Ab wheel",
        ) + g(
            MuscleGroup.KARDIYO,
            "Koşu bandı", "Bisiklet", "Eliptik", "Kürek makinesi", "İp atlama", "Yürüyüş",
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

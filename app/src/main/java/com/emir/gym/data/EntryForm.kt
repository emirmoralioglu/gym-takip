package com.emir.gym.data

/** Form metnini dogrular. Basarisizsa Turkce hata mesaji doner (sessizce kaydetmez). */
sealed interface FormResult {
    data class Ok(val entry: WorkoutEntry) : FormResult
    data class Error(val message: String) : FormResult
}

object EntryForm {
    const val MAX_SETS = 100
    const val MAX_REPS = 1000
    const val MAX_WEIGHT = 1000.0

    fun parse(
        id: Long, epochDay: Long, exercise: String, sets: String, reps: String, weight: String, note: String,
        muscleGroup: String = "",
    ): FormResult {
        val name = exercise.trim()
        if (name.isEmpty()) return FormResult.Error("Egzersiz adı boş olamaz")
        if (name.length > 60) return FormResult.Error("Egzersiz adı en fazla 60 karakter olabilir")
        val s = sets.trim().toIntOrNull()
        if (s == null || s < 1) return FormResult.Error("Set sayısı en az 1 olmalı")
        if (s > MAX_SETS) return FormResult.Error("Set sayısı en fazla $MAX_SETS olabilir")
        val r = reps.trim().toIntOrNull()
        if (r == null || r < 1) return FormResult.Error("Tekrar sayısı en az 1 olmalı")
        if (r > MAX_REPS) return FormResult.Error("Tekrar sayısı en fazla $MAX_REPS olabilir")
        // Turkce klavyede ondalik ayraci virgul olabilir.
        val w = weight.trim().replace(',', '.').toDoubleOrNull()
        if (w == null || w.isNaN() || w.isInfinite() || w < 0) return FormResult.Error("Ağırlık 0 veya daha büyük bir sayı olmalı")
        if (w > MAX_WEIGHT) return FormResult.Error("Ağırlık en fazla ${MAX_WEIGHT.toInt()} kg olabilir")
        val group = MuscleGroup.fromKey(muscleGroup)?.name ?: ""
        return FormResult.Ok(WorkoutEntry(id, epochDay, name, s, r, w, note.trim(), group))
    }
}

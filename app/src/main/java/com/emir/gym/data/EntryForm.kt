package com.emir.gym.data

/** Form metnini dogrular. Basarisizsa Turkce hata mesaji doner (sessizce kaydetmez). */
sealed interface FormResult {
    data class Ok(val entry: WorkoutEntry) : FormResult
    data class Error(val message: String) : FormResult
}

object EntryForm {
    fun parse(
        id: Long, epochDay: Long, exercise: String, sets: String, reps: String, weight: String, note: String,
    ): FormResult {
        val name = exercise.trim()
        if (name.isEmpty()) return FormResult.Error("Egzersiz adı boş olamaz")
        val s = sets.trim().toIntOrNull()
        if (s == null || s < 1) return FormResult.Error("Set sayısı en az 1 olmalı")
        val r = reps.trim().toIntOrNull()
        if (r == null || r < 1) return FormResult.Error("Tekrar sayısı en az 1 olmalı")
        // Turkce klavyede ondalik ayraci virgul olabilir.
        val w = weight.trim().replace(',', '.').toDoubleOrNull()
        if (w == null || w.isNaN() || w.isInfinite() || w < 0) return FormResult.Error("Ağırlık 0 veya daha büyük bir sayı olmalı")
        return FormResult.Ok(WorkoutEntry(id, epochDay, name, s, r, w, note.trim()))
    }
}

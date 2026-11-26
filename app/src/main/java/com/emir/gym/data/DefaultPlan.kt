package com.emir.gym.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Varsayilan haftalik plan (4 gun agirlik, 2 gun yuzme, 1 gun dinlenme).
 * Hareket adlari ExerciseCatalog ile ayni yazilir; boylece kayit/rekor eslemesi calisir.
 */
object DefaultPlan {
    val days: List<PlanDay> = listOf(
        PlanDay(1, "Upper A", PlanDayType.AGIRLIK, "Sonrası: sauna 10-15 dk, duş 5 dk"),
        PlanDay(2, "Lower A", PlanDayType.AGIRLIK, "Sonrası: sauna 10-15 dk, duş 5 dk"),
        PlanDay(3, "Yüzme 1", PlanDayType.YUZME, "~45 dk; sauna isteğe bağlı 10 dk"),
        PlanDay(4, "Upper B", PlanDayType.AGIRLIK, "Sonrası: sauna 10-15 dk, duş 5 dk"),
        PlanDay(5, "Lower B", PlanDayType.AGIRLIK, "Sonrası: sauna 10-15 dk, duş 5 dk"),
        PlanDay(6, "Yüzme 2 (uzun)", PlanDayType.YUZME, "~60 dk; sonrası buhar 8-10 dk"),
        PlanDay(7, "Tam dinlenme", PlanDayType.DINLENME, "Uyku ve beslenme günü"),
    )

    private fun ex(day: Int, pos: Int, name: String, group: MuscleGroup, note: String, posture: Boolean = false) =
        PlanExercise(
            weekday = day, position = pos, name = name, muscleGroup = group.name,
            sets = 3, targetReps = 8, restSeconds = 90, note = note, posture = posture,
        )

    val exercises: List<PlanExercise> = listOf(
        ex(1, 0, "Chest press makinesi", MuscleGroup.GOGUS, "Kontrollü, göğüs açık"),
        ex(1, 1, "Lat pulldown", MuscleGroup.SIRT, "Kürek kemiklerini geri-aşağı çek"),
        ex(1, 2, "Oturarak kablo row", MuscleGroup.SIRT, "Üst sırt, duruş için kritik"),
        ex(1, 3, "Shoulder press makinesi", MuscleGroup.OMUZ, "Omuz kontrolü"),
        ex(1, 4, "Reverse pec deck", MuscleGroup.OMUZ, "Arka omuz + duruş", posture = true),
        ex(2, 0, "Leg press", MuscleGroup.BACAK, "Ana bacak hareketi"),
        ex(2, 1, "Leg curl", MuscleGroup.BACAK, "Hamstring"),
        ex(2, 2, "Leg extension", MuscleGroup.BACAK, "Quad izolasyon"),
        ex(2, 3, "Baldır kaldırma", MuscleGroup.BACAK, "Baldır, tam hareket açıklığı"),
        ex(4, 0, "Incline chest press makinesi", MuscleGroup.GOGUS, "Üst göğüs"),
        ex(4, 1, "Oturarak kablo row", MuscleGroup.SIRT, "Üst sırt, duruş"),
        ex(4, 2, "Yan lateral raise", MuscleGroup.OMUZ, "Makine/cable; yan omuz, hafif ağırlık, kontrollü"),
        ex(4, 3, "Face pull", MuscleGroup.OMUZ, "Rotator cuff + duruş", posture = true),
        ex(5, 0, "Hack squat", MuscleGroup.BACAK, "Veya Smith squat; quad + kalça"),
        ex(5, 1, "Romanian deadlift", MuscleGroup.BACAK, "Smith/makine; hamstring + kalça, sırt düz"),
        ex(5, 2, "Hip abduction", MuscleGroup.BACAK, "Kalça yanı"),
        ex(5, 3, "Baldır kaldırma", MuscleGroup.BACAK, "Baldır"),
    )

    private const val DAY_SQL = "INSERT OR REPLACE INTO `plan_days` (`weekday`, `title`, `type`, `note`) VALUES (?, ?, ?, ?)"
    private const val EX_SQL =
        "INSERT INTO `plan_exercises` (`weekday`, `position`, `name`, `muscleGroup`, `sets`, `targetReps`, " +
            "`restSeconds`, `note`, `posture`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"

    /** Ham SQL ile tohumlar (migration ve onCreate icin; DAO'ya bagli degil). */
    fun seed(db: SupportSQLiteDatabase) {
        days.forEach { db.execSQL(DAY_SQL, arrayOf<Any?>(it.weekday, it.title, it.type.name, it.note)) }
        exercises.forEach {
            db.execSQL(
                EX_SQL,
                arrayOf<Any?>(
                    it.weekday, it.position, it.name, it.muscleGroup, it.sets, it.targetReps,
                    it.restSeconds, it.note, if (it.posture) 1 else 0,
                ),
            )
        }
    }
}

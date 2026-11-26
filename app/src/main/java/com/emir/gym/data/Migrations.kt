package com.emir.gym.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: kas grubu sutunu (eski satirlar bos kalir, mevcut veri korunur)
 * ve ozel egzersiz tablosu. Hicbir tablo silinmez/yeniden olusturulmaz.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workout_entries` ADD COLUMN `muscleGroup` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_exercises` " +
                "(`name` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
    }
}

/**
 * v2 -> v3: plan, aktivite, beslenme tablolari eklenir ve varsayilan plan tohumlanir.
 * Mevcut tablolara dokunulmaz (yikici gecis yok).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createV3Tables(db)
        DefaultPlan.seed(db)
    }
}

internal fun createV3Tables(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `plan_days` (`weekday` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`weekday`))"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `plan_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`weekday` INTEGER NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
            "`muscleGroup` TEXT NOT NULL, `sets` INTEGER NOT NULL, `targetReps` INTEGER NOT NULL, " +
            "`restSeconds` INTEGER NOT NULL, `note` TEXT NOT NULL, `posture` INTEGER NOT NULL)"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_weekday` ON `plan_exercises` (`weekday`)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `activity_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`epochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
            "`distanceM` INTEGER, `note` TEXT NOT NULL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`epochDay` INTEGER NOT NULL, `mealType` TEXT NOT NULL, `description` TEXT NOT NULL, " +
            "`proteinG` REAL NOT NULL, `calories` INTEGER NOT NULL, `carbsG` REAL, `fatG` REAL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `water_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`epochDay` INTEGER NOT NULL, `ml` INTEGER NOT NULL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `body_weights` (`epochDay` INTEGER NOT NULL, `kg` REAL NOT NULL, " +
            "PRIMARY KEY(`epochDay`))"
    )
}

/**
 * Eski Turkce katalog adi -> yeni Ingilizce ad. Kesin (harf katlamasiz) eslesme.
 * Anahtarlar ve degerler birbirinden ayridir (zincirleme yeniden adlandirma olmaz).
 */
val LEGACY_EXERCISE_RENAMES: Map<String, String> = linkedMapOf(
    "Eğimli bench press" to "Incline bench press",
    "Dambıl göğüs press" to "Dumbbell bench press",
    "Eğimli dambıl press" to "Incline dumbbell press",
    "Dambıl fly" to "Dumbbell fly",
    "Kablo crossover" to "Cable crossover",
    "Şınav" to "Push-up",
    "Chest press makinesi" to "Chest press machine",
    "Incline chest press makinesi" to "Incline chest press machine",
    "Barfiks" to "Pull-up",
    "Tek kol dambıl row" to "One-arm dumbbell row",
    "Oturarak kablo row" to "Seated cable row",
    "Sırt ekstansiyonu" to "Back extension",
    "Dambıl omuz press" to "Dumbbell shoulder press",
    "Yan lateral raise" to "Lateral raise",
    "Ön raise" to "Front raise",
    "Arka omuz fly" to "Rear delt fly",
    "Omuz silkme (shrug)" to "Shrug",
    "Shoulder press makinesi" to "Shoulder press machine",
    "Dambıl curl" to "Dumbbell curl",
    "Çekiç curl" to "Hammer curl",
    "French press" to "Overhead triceps extension",
    "Bank dips" to "Bench dips",
    "Ön squat" to "Front squat",
    "Hamle (lunge)" to "Lunge",
    "Baldır kaldırma" to "Calf raise",
    "Bacak kaldırma" to "Leg raise",
    "Ters crunch" to "Reverse crunch",
    "Kablo crunch" to "Cable crunch",
    "Koşu bandı" to "Treadmill",
    "Bisiklet" to "Cycling",
    "Eliptik" to "Elliptical",
    "Kürek makinesi" to "Rowing machine",
    "İp atlama" to "Jump rope",
    "Yürüyüş" to "Walking",
)

/**
 * v3 -> v4: eski Turkce katalog/plan adlari Ingilizce adlara cevrilir (yalniz UPDATE).
 * Ozel egzersiz adlari esleme tablosunda olmadigi icin degismez. Satir silinmez.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        LEGACY_EXERCISE_RENAMES.forEach { (old, new) ->
            val args = arrayOf<Any?>(new, old)
            db.execSQL("UPDATE `workout_entries` SET `exercise` = ? WHERE `exercise` = ?", args)
            db.execSQL("UPDATE `plan_exercises` SET `name` = ? WHERE `name` = ?", args)
            // custom_exercises.name birincil anahtar: hedef ad zaten varsa carpismayi atla.
            db.execSQL("UPDATE OR IGNORE `custom_exercises` SET `name` = ? WHERE `name` = ?", args)
        }
    }
}

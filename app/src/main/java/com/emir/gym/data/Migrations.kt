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

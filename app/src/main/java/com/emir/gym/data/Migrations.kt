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

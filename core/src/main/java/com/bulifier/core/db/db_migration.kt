package com.bulifier.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE history ADD COLUMN progress REAL NOT NULL DEFAULT -1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE schema_settings RENAME COLUMN file_extension TO output_extension")
        database.execSQL("ALTER TABLE schema_settings ADD COLUMN input_extension TEXT DEFAULT 'bul' NOT NULL")
    }
}
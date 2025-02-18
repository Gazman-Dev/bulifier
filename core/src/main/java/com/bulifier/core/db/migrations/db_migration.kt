package com.bulifier.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.intellij.lang.annotations.Language

const val DB_VERSION = 16

fun SupportSQLiteDatabase.execSql(@Language("RoomSql") sql: String) = execSQL(sql)

private val migrationFunctions = arrayOf<(SupportSQLiteDatabase) -> Unit>(
    {
        it.execSql("ALTER TABLE history ADD COLUMN progress REAL NOT NULL DEFAULT -1")
    },
    {
        it.execSql("ALTER TABLE schema_settings RENAME COLUMN file_extension TO output_extension")
        it.execSql("ALTER TABLE schema_settings ADD COLUMN input_extension TEXT DEFAULT 'bul' NOT NULL")
    },
    {
        it.execSql("ALTER TABLE projects ADD COLUMN project_details TEXT")
    },
    {
        it.execSql("ALTER TABLE schema_settings ADD COLUMN purpose TEXT DEFAULT 'TBA' NOT NULL")
        it.execSql("ALTER TABLE schema_settings ADD COLUMN agent INTEGER DEFAULT 0 NOT NULL")
    },
    {
        it.execSql("ALTER TABLE schema_settings ADD COLUMN purpose TEXT DEFAULT 'TBA' NOT NULL")
        it.execSql("ALTER TABLE schema_settings ADD COLUMN agent INTEGER DEFAULT 0 NOT NULL")
    }, {
        it.execSql("ALTER TABLE schema_settings ADD COLUMN visible_for_agent INTEGER DEFAULT 0 NOT NULL")
    },
    migration7,
    {
        it.execSql("ALTER TABLE files ADD COLUMN to_delete INTEGER NOT NULL DEFAULT 0")
        it.execSql("CREATE INDEX index_files_project_id_to_delete ON files(project_id, to_delete)")
    },
    {
        it.execSql("DROP INDEX IF EXISTS index_files_path_project_id")
        it.execSql("CREATE INDEX index_files_to_delete_path_project_id ON files(to_delete, path, project_id)")
    },
    {
        it.execSql("ALTER TABLE history ADD COLUMN created INTEGER NOT NULL DEFAULT 0")
    },
    {
        it.execSql("ALTER TABLE projects ADD COLUMN template TEXT")
    },
    {
        it.execSql("ALTER TABLE files ADD COLUMN is_binary INTEGER NOT NULL DEFAULT 0")
    },
    migration13,
    migration14,
    {
        it.execSql("ALTER TABLE history ADD COLUMN cost REAL default null")
    },
    {
        it.execSql("ALTER TABLE history ADD COLUMN native_code INTEGER NOT NULL DEFAULT 0")
    }
)

val MIGRATIONS = migrationFunctions.mapIndexed { index, migrationFunction ->
    val startVersion = index
    val endVersion = index + 1
    object : Migration(startVersion, endVersion) {
        override fun migrate(database: SupportSQLiteDatabase) {
            migrationFunction(database)
        }
    }
}.toTypedArray().apply {
    if (size != DB_VERSION) {
        throw Error("Update db_migration.DB_VERSION to $size")
    }
}

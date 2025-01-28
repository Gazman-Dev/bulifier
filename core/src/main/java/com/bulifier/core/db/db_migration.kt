package com.bulifier.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val DB_VERSION = 10

private val migrationFunctions = arrayOf<(SupportSQLiteDatabase) -> Unit>(
    {
        it.execSQL("ALTER TABLE history ADD COLUMN progress REAL NOT NULL DEFAULT -1")
    },
    {
        it.execSQL("ALTER TABLE schema_settings RENAME COLUMN file_extension TO output_extension")
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN input_extension TEXT DEFAULT 'bul' NOT NULL")
    },
    {
        it.execSQL("ALTER TABLE projects ADD COLUMN project_details TEXT")
    },
    {
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN purpose TEXT DEFAULT 'TBA' NOT NULL")
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN agent INTEGER DEFAULT 0 NOT NULL")
    },
    {
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN purpose TEXT DEFAULT 'TBA' NOT NULL")
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN agent INTEGER DEFAULT 0 NOT NULL")
    }, {
        it.execSQL("ALTER TABLE schema_settings ADD COLUMN visible_for_agent INTEGER DEFAULT 0 NOT NULL")
    },
    {
        it.execSQL("ALTER TABLE files ADD COLUMN hash INTEGER DEFAULT -2 NOT NULL")
        it.execSQL("ALTER TABLE files ADD COLUMN sync_hash INTEGER DEFAULT -1 NOT NULL")
        it.execSQL("ALTER TABLE `schema_settings` RENAME TO `schema_settings_old`")
        it.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `schema_settings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `schema_name` TEXT NOT NULL,
                `purpose` TEXT NOT NULL,
                `visible_for_agent` INTEGER NOT NULL,
                `input_extension` TEXT NOT NULL,
                `processing_mode` TEXT NOT NULL,
                `multi_files_output` INTEGER NOT NULL,
                `override_files` INTEGER NOT NULL,
                `agent` INTEGER NOT NULL,
                `project_id` INTEGER NOT NULL,
                FOREIGN KEY(`project_id`) REFERENCES `projects`(`project_id`) ON DELETE CASCADE
            )
        """.trimIndent()
        )
        it.execSQL(
            """
            INSERT INTO `schema_settings` (
                `id`,
                `schema_name`,
                `purpose`,
                `visible_for_agent`,
                `input_extension`,
                `processing_mode`,
                `multi_files_output`,
                `override_files`,
                `agent`,
                `project_id`
            )
            SELECT
                `id`,
                `schema_name`,
                `purpose`,
                `visible_for_agent`,
                `input_extension`,
                CASE WHEN `run_for_each_file` = 0 THEN 'SINGLE' ELSE 'PER_FILE' END,
                `multi_files_output`,
                `override_files`,
                `agent`,
                `project_id`
            FROM `schema_settings_old`
        """.trimIndent()
        )
        it.execSQL("DROP TABLE `schema_settings_old`")
        it.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_schema_settings_schema_name_project_id`
            ON `schema_settings` (`schema_name`,`project_id`)
        """.trimIndent()
        )
        it.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_schema_settings_project_id`
            ON `schema_settings` (`project_id`)
        """.trimIndent()
        )

        it.execSQL(
            """
    CREATE TABLE IF NOT EXISTS `sync_files` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `file_id` INTEGER,
        `raw_file_id` INTEGER,
        `bullets_file_id` INTEGER NOT NULL,
        `schema` TEXT NOT NULL,
        `project_id` INTEGER NOT NULL,
        `last_updated` INTEGER NOT NULL,
        FOREIGN KEY(`project_id`) REFERENCES `projects`(`project_id`) ON DELETE CASCADE
    )
""".trimIndent()
        )

        it.execSQL(
            """
    CREATE INDEX IF NOT EXISTS `index_sync_files_project_id_schema`
    ON `sync_files` (`project_id`, `schema`)
""".trimIndent()
        )
    },
    {
        it.execSQL("ALTER TABLE files ADD COLUMN to_delete INTEGER NOT NULL DEFAULT 0")
        it.execSQL("CREATE INDEX index_files_project_id_to_delete ON files(project_id, to_delete)")
    },
    {
        it.execSQL("DROP INDEX IF EXISTS index_files_path_project_id")
        it.execSQL("CREATE INDEX index_files_to_delete_path_project_id ON files(to_delete, path, project_id)")
    },
    {
        it.execSQL("ALTER TABLE history ADD COLUMN created INTEGER NOT NULL DEFAULT 0")
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

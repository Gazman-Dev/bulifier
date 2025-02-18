package com.bulifier.core.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration7: (SupportSQLiteDatabase) -> Unit = {
    it.execSql("ALTER TABLE files ADD COLUMN hash INTEGER DEFAULT -2 NOT NULL")
    it.execSql("ALTER TABLE files ADD COLUMN sync_hash INTEGER DEFAULT -1 NOT NULL")
    it.execSql("ALTER TABLE `schema_settings` RENAME TO `schema_settings_old`")
    it.execSql(
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
    it.execSql(
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
    it.execSql("DROP TABLE `schema_settings_old`")
    it.execSql(
        """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_schema_settings_schema_name_project_id`
            ON `schema_settings` (`schema_name`,`project_id`)
        """.trimIndent()
    )
    it.execSql(
        """
            CREATE INDEX IF NOT EXISTS `index_schema_settings_project_id`
            ON `schema_settings` (`project_id`)
        """.trimIndent()
    )

    it.execSql(
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

    it.execSql(
        """
    CREATE INDEX IF NOT EXISTS `index_sync_files_project_id_schema`
    ON `sync_files` (`project_id`, `schema`)
""".trimIndent()
    )
}

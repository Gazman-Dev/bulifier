package com.bulifier.core.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration14: (SupportSQLiteDatabase) -> Unit = {
    it.execSql("drop table sync_files")
    migrateTable(
        it, "history", mapOf(
            "sync_raw_files" to "",
            "sync_bullet_files" to ""
        )
    ) {
        it.execSql(
            """
            CREATE TABLE history (
                schema            TEXT NOT NULL,
                error_message     TEXT,
                last_updated      INTEGER NOT NULL,
                file_name         TEXT,
                model_id          TEXT,
                context_files     TEXT NOT NULL,
                sync_raw_files    TEXT NOT NULL,
                path              TEXT NOT NULL,
                project_id        INTEGER NOT NULL,
                prompt_id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sync_bullet_files TEXT NOT NULL,
                prompt            TEXT NOT NULL,
                status            TEXT NOT NULL,
                FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Create the required indexes:
        it.execSql("CREATE INDEX index_history_last_updated ON history(last_updated)")
        it.execSql("CREATE INDEX index_history_project_id ON history(project_id)")
    }
    migrateTable(
        it, "schema_settings", mapOf(
            "multi_raw_files_output" to 0
        )
    ) {
        it.execSql(
            """
                CREATE TABLE IF NOT EXISTS schema_settings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    schema_name TEXT NOT NULL,
                    purpose TEXT NOT NULL,
                    visible_for_agent INTEGER NOT NULL DEFAULT 0,
                    multi_files_output INTEGER NOT NULL DEFAULT 0,
                    multi_raw_files_output INTEGER NOT NULL DEFAULT 0,
                    agent INTEGER NOT NULL DEFAULT 0,
                    project_id INTEGER NOT NULL,
                    FOREIGN KEY(project_id) REFERENCES projects(project_id) ON DELETE CASCADE
                )
            """.trimIndent()
        )

        it.execSql(
            """
                -- Create a unique index on (schema_name, project_id)
                CREATE UNIQUE INDEX IF NOT EXISTS index_schema_settings_schema_name_project_id 
                    ON schema_settings(schema_name, project_id);
                """.trimIndent()
        )
        it.execSql(
            """
                -- Create an index on project_id
                CREATE INDEX IF NOT EXISTS index_schema_settings_project_id 
                ON schema_settings(project_id);
                """.trimIndent()
        )
    }
    migrateTable(it, "contents") {
        it.execSql(
            """
                -- Create the 'contents' table with a foreign key constraint on file_id
                CREATE TABLE IF NOT EXISTS "contents" (
                    "file_id" INTEGER NOT NULL,
                    "content" TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY("file_id"),
                    FOREIGN KEY("file_id") REFERENCES files(file_id) ON DELETE CASCADE
                )
                """.trimIndent()
        )
    }
}
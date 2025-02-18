package com.bulifier.core.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

internal val migration13: (SupportSQLiteDatabase) -> Unit = { it ->
    it.execSql(
        """
            CREATE TABLE IF NOT EXISTS dependencies (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                file_id INTEGER NOT NULL,
                dependency_file_id INTEGER NOT NULL,
                project_id INTEGER NOT NULL,
                last_updated INTEGER NOT NULL,
                FOREIGN KEY (project_id) REFERENCES projects (project_id) ON DELETE CASCADE,
                FOREIGN KEY (file_id) REFERENCES files (file_id) ON DELETE CASCADE,
                FOREIGN KEY (dependency_file_id) REFERENCES files (file_id) ON DELETE CASCADE
            )
            """.trimIndent()
    )

    // Create the unique index
    it.execSql(
        """
            CREATE UNIQUE INDEX index_dependencies_project_id_file_id_dependency_file_id 
            ON dependencies (project_id, file_id, dependency_file_id)
            """.trimIndent()
    )
}

package com.bulifier.core.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase

fun migrateTable(
    db: SupportSQLiteDatabase,
    tableName: String,
    defaultValues: Map<String, Any?> = emptyMap(),
    newSchemaCallback: () -> Unit
) {
    // Step 1: Rename the original table to a temporary table.
    val tempTableName = "${tableName}_temp"
    db.execSQL("ALTER TABLE $tableName RENAME TO $tempTableName")

    // Step 1.1: Drop all indexes associated with the renamed table.
    val indexNames = getTableIndexes(db, tempTableName)
    indexNames.forEach { indexName ->
        db.execSQL("DROP INDEX IF EXISTS $indexName")
    }

    // Step 2: Execute all new schema commands to create the new table.
    newSchemaCallback()

    // Step 3: Copy data from the temp table to the new table.
    // Retrieve columns from the new table (in order) and the old temp table.
    val newTableColumns = getTableColumns(db, tableName) // List<String>
    val tempTableColumns = getTableColumns(db, tempTableName).toSet() // For membership testing

    // For each new table column, determine what to select:
    // - If it exists in the old table, select the column.
    // - Otherwise, if a default value is provided, convert it to a SQL literal and alias it.
    // - Otherwise, select NULL.
    val selectExpressions = newTableColumns.map { column ->
        when {
            tempTableColumns.contains(column) -> column
            defaultValues.containsKey(column) ->
                "${toSqlLiteral(defaultValues[column])} AS $column"

            else -> "NULL AS $column"
        }
    }

    val columnsSeparated = newTableColumns.joinToString(", ")
    val selectExprSeparated = selectExpressions.joinToString(", ")

    db.execSQL(
        "INSERT INTO $tableName ($columnsSeparated) SELECT $selectExprSeparated FROM $tempTableName"
    )

    // Step 4: Drop the temporary table.
    db.execSQL("DROP TABLE $tempTableName")
}

// Helper function to retrieve table columns via PRAGMA in the order they were created.
private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): List<String> {
    val columns = mutableListOf<String>()
    val cursor = db.query("PRAGMA table_info($tableName)")
    cursor.use {
        val nameColumnIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameColumnIndex != -1) {
                columns.add(cursor.getString(nameColumnIndex))
            }
        }
    }
    return columns
}

// Helper function to retrieve index names for a given table.
private fun getTableIndexes(db: SupportSQLiteDatabase, tableName: String): List<String> {
    val indexes = mutableListOf<String>()
    val cursor =
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$tableName'")
    cursor.use {
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex != -1) {
                indexes.add(cursor.getString(nameIndex))
            }
        }
    }
    return indexes
}

// Helper function to convert an object to its SQL literal representation.
private fun toSqlLiteral(value: Any?): String {
    return when (value) {
        null -> "NULL"
        is String -> "'${value.replace("'", "''")}'"
        is Number -> value.toString()
        is Boolean -> if (value) "1" else "0"
        else -> throw IllegalArgumentException("Unsupported default type: ${value::class.java}")
    }
}



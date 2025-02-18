@file:Suppress("FunctionName")

package com.bulifier.core.db

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bulifier.core.db.migrations.MIGRATIONS
import com.bulifier.core.utils.fullPath
import java.util.concurrent.Executors
import java.util.zip.Adler32

val Context.db: AppDatabase
    get() = getDatabase(this)

private var INSTANCE: AppDatabase? = null
private fun getDatabase(context: Context): AppDatabase {
    return INSTANCE ?: synchronized("") {
        val instance = INSTANCE ?: Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).setQueryCallback(object : RoomDatabase.QueryCallback {
            override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                Log.d("room", "SQL Query: \n$sqlQuery\nBind Args: $bindArgs")
            }
        }, Executors.newSingleThreadExecutor())
            .addTypeConverter(DateTypeConverter())
            .addTypeConverter(SetConverter())
            .addTypeConverter(LongListConverter())
            .addMigrations(*MIGRATIONS)
            .build()
        INSTANCE = instance
        instance
    }
}

val checksum = Adler32()

data class HistoryItemWithSelection(
    @Embedded val historyItem: HistoryItem,

    @ColumnInfo(name = "selected")
    val selected: Boolean,

    @ColumnInfo(name = "agent_order")
    val agentOrder: Boolean
)

@Keep
enum class SyncMode {
    BULLET,
    RAW,
    AUTO
}

data class FileData(
    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "is_file")
    val isFile: Boolean,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "content")
    val content: String = "",
) {
    val fullPath get() = fullPath(path, fileName)
}
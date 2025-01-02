@file:Suppress("FunctionName")

package com.bulifier.core.db

import android.content.Context
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Room
import com.bulifier.core.db.Content.Type
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
        )
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
    val selected: Boolean
)

@Keep
enum class SyncMode{
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

    @ColumnInfo(name = "type")
    val type: Type
) {
    fun toContent() = Content(
        fileId = fileId,
        content = content,
        type = type
    )
}
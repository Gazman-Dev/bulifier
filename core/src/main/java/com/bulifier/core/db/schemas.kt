package com.bulifier.core.db

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ProvidedTypeConverter
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.bulifier.core.prefs.Prefs
import java.lang.Long.parseLong
import java.util.Date

@Database(
    entities = [
        Project::class,
        File::class,
        Content::class,
        HistoryItem::class,
        Schema::class,
        ResponseItem::class
    ], version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao

    abstract fun historyDao(): HistoryDao

    abstract fun schemaDao(): SchemaDao
}

@ProvidedTypeConverter
class DateTypeConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@ProvidedTypeConverter
class LongListConverter {
    @TypeConverter
    fun fromLongList(value: List<Long>): String {
        return value.joinToString()
    }

    @TypeConverter
    fun stringToLongList(value: String): List<Long> = value.split(",")
        .filter { it.isNotBlank() }
        .map {
            parseLong(it.trim())
        }
}

@ProvidedTypeConverter
class SetConverter {
    private companion object {
        const val SEPARATOR = "||"
    }

    @TypeConverter
    fun fromSet(value: LinkedHashSet<String>): String {
        return value.joinToString(SEPARATOR)
    }

    @TypeConverter
    fun toSet(value: String): LinkedHashSet<String> {
        return LinkedHashSet(value.split(SEPARATOR))
    }
}

@Entity(tableName = "projects", indices = [Index(value = ["project_id"], unique = true)])
@TypeConverters(DateTypeConverter::class)
data class Project(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "project_id")
    val projectId: Long = 0,

    @ColumnInfo(name = "project_name")
    val projectName: String,

    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()
)

@Entity(tableName = "schemas", indices = [Index(value = ["schema_name"])])
@TypeConverters(SetConverter::class)
data class Schema(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "schema_id")
    val schemaId: Long = 0,

    @ColumnInfo(name = "schema_name")
    val schemaName: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "type")
    val type: SchemaType,

    @ColumnInfo(name = "keys")
    val keys: LinkedHashSet<String>
)

enum class SchemaType {
    SYSTEM,
    USER,
    COMMENT,
    CONTEXT;

    companion object {
        fun fromString(value: String) = when (value.lowercase().trim()) {
            "system" -> SYSTEM
            "system-loop" -> CONTEXT
            "user" -> USER
            "comment" -> COMMENT
            else -> throw IllegalArgumentException("Invalid schema type: $value")
        }
    }
}

@Entity(
    tableName = "files",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = arrayOf("project_id"),
        childColumns = arrayOf("project_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["path", "project_id"]),
        Index(value = ["path", "file_name", "project_id"], unique = true)]
)
data class File(

    @ColumnInfo(name = "file_id")
    @PrimaryKey(autoGenerate = true)
    val fileId: Long = 0,

    @ColumnInfo(name = "project_id")
    val projectId: Long,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "is_file")
    val isFile: Boolean,

    @ColumnInfo(name = "size")
    val size: Int = 0,

    @ColumnInfo(name = "file_count")
    val filesCount: Int = 0
)

@Entity(
    tableName = "contents",
    foreignKeys = [ForeignKey(
        entity = File::class,
        parentColumns = arrayOf("file_id"),
        childColumns = arrayOf("file_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["file_id"], unique = true)]
)
data class Content(
    @PrimaryKey
    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "type")
    val type: Type = Type.NONE
){
    enum class Type{
        BULLET,
        RAW,
        SCHEMA,
        NONE
    }
}

@Entity(
    tableName = "history",
    indices = [Index(value = ["last_updated"])]
)
@TypeConverters(DateTypeConverter::class, LongListConverter::class)
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "prompt_id")
    val promptId: Long = 0,

    @ColumnInfo(name = "model_id")
    val modelId: String? = null,

    @ColumnInfo(name = "schema_field_id")
    val schemaFileId: Long = 0,

    @ColumnInfo(name = "prompt")
    val prompt: String = "",

    @ColumnInfo(name = "status")
    val status: HistoryStatus = HistoryStatus.PROMPTING,

    @ColumnInfo(name = "context_files")
    val contextFiles: List<Long> = emptyList(),

    @ColumnInfo(name = "schema")
    val schema:String = SCHEMA_BULLIFY,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_name")
    val fileName: String?,

    @ColumnInfo(name = "project_id")
    val projectId: Long = Prefs.projectId.value!!,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
){
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()

    companion object{
        const val SCHEMA_BULLIFY = "bulify"
        const val SCHEMA_DEBULIFY = "debulify"
        const val SCHEMA_REBULIFY_FILE = "rebulify-file"
    }
}

@Entity(
    tableName = "responses",
    indices = [Index(value = ["last_updated"])],
    foreignKeys = [
        ForeignKey(
            entity = HistoryItem::class,
            parentColumns = ["prompt_id"],
            childColumns = ["prompt_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(DateTypeConverter::class)
data class ResponseItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "prompt_id")
    val promptId: Long,

    @ColumnInfo(name = "response")
    val response: String = "",

    @ColumnInfo(name = "request")
    val request: String
){
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()
}


enum class HistoryStatus {
    PROMPTING,
    SUBMITTED, // loading
    RESPONDED, // completed
    ERROR
}
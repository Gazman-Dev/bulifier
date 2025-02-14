package com.bulifier.core.db

import android.content.Context
import androidx.annotation.Keep
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
        ResponseItem::class,
        SchemaSettings::class,
        SyncFile::class
    ], version = DB_VERSION
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao

    abstract fun historyDao(): HistoryDao

    abstract fun schemaDao(): SchemaDao

    abstract fun syncDao(): SyncDao
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

@Entity(
    tableName = "projects", indices =
    [
        Index(value = ["project_name"], unique = true)
    ]
)
@TypeConverters(DateTypeConverter::class)
data class Project(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "project_id")
    val projectId: Long = 0,

    @ColumnInfo(name = "project_name")
    val projectName: String,

    @ColumnInfo(name = "project_details")
    val projectDetails: String? = null,

    @ColumnInfo(name = "template")
    val template: String? = null,

    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()
)

@Entity(
    tableName = "schemas", foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = arrayOf("project_id"),
        childColumns = arrayOf("project_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["schema_name"]),
        Index(value = ["project_id"])
    ]
)
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
    val keys: LinkedHashSet<String>,

    @ColumnInfo(name = "project_id")
    val projectId: Long,
)

@Entity(
    tableName = "schema_settings", foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = arrayOf("project_id"),
        childColumns = arrayOf("project_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["schema_name", "project_id"], unique = true),
        Index(value = ["project_id"])
    ]
)
data class SchemaSettings(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "schema_name")
    val schemaName: String,

    @ColumnInfo(name = "purpose")
    val purpose: String,

    @ColumnInfo(name = "visible_for_agent")
    val visibleForAgent: Boolean = false,

    @ColumnInfo(name = "input_extension")
    val inputExtension: String = "bul",

    @ColumnInfo(name = "processing_mode")
    val processingMode: ProcessingMode = ProcessingMode.SINGLE,

    @ColumnInfo(name = "multi_files_output")
    val multiFilesOutput: Boolean = false,

    @ColumnInfo(name = "override_files")
    val overrideFiles: Boolean = false,

    @ColumnInfo(name = "agent")
    val isAgent: Boolean = false,

    @ColumnInfo(name = "project_id")
    val projectId: Long,
)

@Keep
enum class ProcessingMode {
    PER_FILE,        // Process each file individually
    SINGLE,          // Process all files together
    SYNC_BULLETS,    // Update bullet point files with corresponding raw files
    SYNC_RAW;        // Update raw files with corresponding bullet point files

    companion object {
        fun fromString(value: String) = when (value.lowercase().trim()) {
            "per_file" -> PER_FILE
            "single" -> SINGLE
            "sync_bullets" -> SYNC_BULLETS
            "sync_raw" -> SYNC_RAW
            else -> throw IllegalArgumentException("Invalid processing mode: $value")
        }
    }
}

enum class SchemaType {
    SYSTEM,
    USER,
    COMMENT,
    SETTINGS,
    CONTEXT;

    companion object {
        fun fromString(value: String) = when (value.lowercase().trim()) {
            "system" -> SYSTEM
            "system-loop" -> CONTEXT
            "user" -> USER
            "comment" -> COMMENT
            "settings" -> SETTINGS
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
        Index(value = ["to_delete", "path", "project_id"]),
        Index(
            value = ["path", "file_name", "project_id"],
            unique = true
        ),
        Index(value = ["project_id"]),
        Index(value = ["project_id", "to_delete"])
    ],
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
    val size: Long = 0,

    @ColumnInfo(name = "hash")
    val hash: Long = -1,

    @ColumnInfo(name = "sync_hash")
    val syncHash: Long = -1,

    @ColumnInfo(name = "to_delete")
    val delete: Boolean = false,

    @ColumnInfo(name = "is_binary")
    val isBinary: Boolean = false,
){
    fun getBinaryFile(context: Context) = getBinaryFile(context, fileId, projectId)
}

fun getBinaryFile(context: Context, fileId: Long, projectId: Long = Prefs.projectId.flow.value) =
    java.io.File(context.filesDir, "binary/$projectId/$fileId")

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

    @Deprecated("Not used anymore")
    @ColumnInfo(name = "type")
    val type: Type = Type.NONE
) {
    enum class Type {
        BULLET,
        RAW,
        SCHEMA,
        NONE
    }
}

@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = arrayOf("project_id"),
        childColumns = arrayOf("project_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["last_updated"]), Index(value = ["project_id"])]
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

    @ColumnInfo(name = "progress")
    val progress: Float = -1f,

    @ColumnInfo(name = "context_files")
    val contextFiles: List<Long> = emptyList(),

    @ColumnInfo(name = "schema")
    val schema: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_name")
    val fileName: String?,

    @ColumnInfo(name = "project_id")
    val projectId: Long = Prefs.projectId.flow.value,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created")
    var created: Date = Date()
) {
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()

    companion object {
        const val SCHEMA_AGENT = "agent"
        const val SCHEMA_UPDATE_RAW_WITH_BULLETS = "update-raw-with-bullet"
        const val SCHEMA_DEBULIFY_FILE = "debulify-file"
        const val SCHEMA_UPDATE_BULLET_WITH_RAW = "update-bullet-with-raw"
    }
}

@Entity(
    tableName = "responses",
    indices = [Index(value = ["last_updated"]), Index(value = ["prompt_id"])],
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
) {
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()
}

@Entity(
    tableName = "sync_files",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = arrayOf("project_id"),
        childColumns = arrayOf("project_id"),
        onDelete = ForeignKey.CASCADE
    )], indices = [Index(value = ["project_id", "schema"])]
)
@TypeConverters(DateTypeConverter::class)
data class SyncFile(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "file_id")
    val fileId: Long?,

    @ColumnInfo(name = "raw_file_id")
    val rawFileId: Long?,

    @ColumnInfo(name = "bullets_file_id")
    val bulletsFileId: Long,

    @ColumnInfo(name = "schema")
    val schema: String,

    @ColumnInfo(name = "project_id")
    val projectId: Long,
) {
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()
}


enum class HistoryStatus {
    PROMPTING,
    RE_APPLYING, // reapplying
    SUBMITTED, // submitted
    PROCESSING, // loading
    RESPONDED, // completed
    ERROR
}
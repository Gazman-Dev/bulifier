package com.bulifier.core.db

import android.content.Context
import android.util.Log
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
import com.bulifier.core.db.migrations.DB_VERSION
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.utils.fullPath
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
        Dependency::class
    ], version = DB_VERSION
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
        .map {
            it.trim()
        }
        .filter { it.isNotEmpty() && it.trim() != "null" }
        .map {
            parseLong(it)
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

    @ColumnInfo(name = "multi_files_output")
    val multiFilesOutput: Boolean = false,

    @ColumnInfo(name = "multi_raw_files_output")
    val multiRawFilesOutput: Boolean = false,

    @ColumnInfo(name = "agent")
    val isAgent: Boolean = false,

    @ColumnInfo(name = "project_id")
    val projectId: Long,
)

enum class SchemaType {
    SYSTEM,
    USER,
    COMMENT,
    SETTINGS;

    companion object {
        fun fromString(value: String) = when (value.lowercase().trim()) {
            "system" -> SYSTEM
            "user" -> USER
            "comment" -> COMMENT
            "settings" -> SETTINGS
            else -> {
                Log.e("SchemaType", "Unknown schema type: $value")
                COMMENT
            }
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
) {
    fun getBinaryFile(context: Context) = getBinaryFile(context, fileId, projectId)

    val fullPath: String
        get() = fullPath(path, fileName)
}

fun getBinaryFile(
    context: Context,
    fileId: Long,
    projectId: Long = AppSettings.project.value.projectId
) =
    java.io.File(context.filesDir, "binary/$projectId/$fileId")

@Entity(
    tableName = "contents",
    foreignKeys = [ForeignKey(
        entity = File::class,
        parentColumns = arrayOf("file_id"),
        childColumns = arrayOf("file_id"),
        onDelete = ForeignKey.CASCADE
    )]
)
data class Content(
    @PrimaryKey
    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "content")
    val content: String = ""
)

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

    @ColumnInfo(name = "prompt")
    val prompt: String = "",

    @ColumnInfo(name = "status")
    val status: HistoryStatus = HistoryStatus.PROMPTING,

    @ColumnInfo(name = "context_files")
    val contextFiles: List<Long> = emptyList(),

    @ColumnInfo(name = "sync_bullet_files")
    val syncBulletFiles: List<Long> = emptyList(),

    @ColumnInfo(name = "sync_raw_files")
    val syncRawFiles: List<Long> = emptyList(),

    @ColumnInfo(name = "schema")
    val schema: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_name")
    val fileName: String?,

    @ColumnInfo(name = "project_id")
    val projectId: Long = AppSettings.project.value.projectId,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "cost")
    val cost: Double? = null,

    @ColumnInfo(name = "native_code")
    val isNativeCode: Boolean = false
) {
    @ColumnInfo(name = "last_updated")
    var lastUpdated: Date = Date()

    companion object {
        const val SCHEMA_AGENT = "agent"
        const val SCHEMA_DEBULIFY = "debulify"
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
    tableName = "dependencies",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = arrayOf("project_id"),
            childColumns = arrayOf("project_id"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = File::class,
            parentColumns = arrayOf("file_id"),
            childColumns = arrayOf("file_id"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = File::class,
            parentColumns = arrayOf("file_id"),
            childColumns = arrayOf("dependency_file_id"),
            onDelete = ForeignKey.CASCADE
        )
    ], indices = [Index(value = ["project_id", "file_id", "dependency_file_id"], unique = true)]
)
@TypeConverters(DateTypeConverter::class)
data class Dependency(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "dependency_file_id")
    val dependencyFileId: Long,

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
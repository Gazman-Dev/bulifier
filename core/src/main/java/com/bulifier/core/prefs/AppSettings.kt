package com.bulifier.core.prefs

import android.content.Context
import com.bulifier.core.db.Content
import com.bulifier.core.db.File
import com.bulifier.core.db.Project
import com.bulifier.core.db.db
import com.bulifier.core.prefs.Prefs.pref
import com.bulifier.core.utils.Logger
import com.bulifier.core.utils.loadAssets
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val KET_PROJECT_ID = "project_id"
private const val KEY_PROJECT_NAME = "project_name"
private const val KET_PROJECT_DETAILS = "project_details"
private const val KEY_TEMPLATES = "template"

object AppSettings {
    val appLogger = Logger("app")
    private val job = Job()
    val scope = CoroutineScope(Dispatchers.Default + job)
    const val SETTINGS_BULIFIER = "bulifier.yaml"
    lateinit var appContext: Context
        private set
    private val db by lazy { appContext.db }

    // Lazy initialization: read initial values from SharedPreferences.
    val project: StateFlow<Project> by lazy {
        MutableStateFlow(readProjectFromPrefs())
    }
    val projectSettings: StateFlow<ProjectSettings> =
        MutableStateFlow(ProjectSettings(emptyList(), emptyMap()))

    val path by lazy { PrefStringValue("path") }
    val models by lazy { PrefListValue("models") }

    // Helper to read from SharedPreferences.
    private fun readProjectFromPrefs(): Project {
        val id = pref.getLong(KET_PROJECT_ID, -1L)
        val name = pref.getString(KEY_PROJECT_NAME, "") ?: ""
        val details = pref.getString(KET_PROJECT_DETAILS, null)
        val template = pref.getString(KEY_TEMPLATES, null)
        return Project(
            projectId = id,
            projectName = name,
            template = template,
            projectDetails = details
        )
    }

    // Atomically update the project.
    fun updateProject(project: Project) {
        // Update SharedPreferences.
        pref.edit()
            .putLong(KET_PROJECT_ID, project.projectId)
            .putString(KEY_PROJECT_NAME, project.projectName)
            .putString(KET_PROJECT_DETAILS, project.projectDetails ?: "")
            .putString(KEY_TEMPLATES, project.template ?: "")
            .apply()

        // Update the in-memory state atomically.
        (this@AppSettings.project as MutableStateFlow<Project>).value = project
        path.set("")
    }

    // Clear the project data.
    fun clear() {
        updateProject(
            Project(
                projectId = -1L,
                projectName = "",
                template = null,
                projectDetails = null
            )
        )
    }

    fun initialize(context: Context) {
        pref = context.getSharedPreferences("buly", 0)
        appContext = context
    }

    suspend fun reloadSettings(loadFromAssets: Boolean = false) {
        val settings = if (loadFromAssets || !appContext.db.fileDao()
                .isFileExists("", SETTINGS_BULIFIER, project.value.projectId)
        ) {
            loadSettingsFromAssets()
        } else {
            loadSettingsFromDb()
        }

        projectSettings as MutableStateFlow<ProjectSettings>
        projectSettings.value = settings
    }

    suspend fun loadSettingsFromDb() = parseSettings(
        db.fileDao().getContent("", SETTINGS_BULIFIER, project.value.projectId)?.content
    )


    private fun parseSettings(yamlContent: String?): ProjectSettings {
        val mapper = ObjectMapper(YAMLFactory()).apply {
            registerModule(KotlinModule.Builder().build())
        }
        // Construct the type for Map<String, Any>
        val mapType = mapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
        val data: Map<String, Any> = mapper.readValue(yamlContent ?: "{}", mapType)

        // Extract the "protected" key (renamed to protectedPaths) safely
        val protectedPaths = (data["protected"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        // Extract the "models" key safely as a map of String to String
        val models = (data["models"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            if (k is String && v is String) k to v else null
        }?.toMap() ?: emptyMap()

        return ProjectSettings(protectedPaths, models)
    }

    private suspend fun loadSettingsFromAssets(): ProjectSettings {
        val project = this@AppSettings.project.value

        val content = loadAssets(appContext, SETTINGS_BULIFIER)
        var fileId = db.fileDao().insertFile(
            File(
                path = "",
                fileName = SETTINGS_BULIFIER,
                isFile = true,
                projectId = project.projectId
            )
        )
        if (fileId == -1L) {
            fileId = db.fileDao().getFileId("", SETTINGS_BULIFIER, project.projectId)!!
            db.fileDao().updateContentAndFileMetaData(
                Content(
                    fileId = fileId,
                    content = content
                )
            )
        } else {
            db.fileDao().insertContentAndUpdateFileMetaData(
                Content(
                    fileId = fileId,
                    content = content
                )
            )
        }

        return parseSettings(content)
    }
}

data class ProjectSettings(
    val protectedPaths: List<String>,
    val models: Map<String, String>
)

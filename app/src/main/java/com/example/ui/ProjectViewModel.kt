package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AndroidProjectGenerator
import com.example.core.MoshiHelper
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ProjectDatabase.getDatabase(application)
    private val repository = ProjectRepository(application, db.projectDao())
    private val projectGenerator = AndroidProjectGenerator(application, repository)

    val projects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedProject = MutableStateFlow<Project?>(null)
    val filesTree = MutableStateFlow<List<FileNode>>(emptyList())
    val selectedFile = MutableStateFlow<File?>(null)
    val selectedFileContent = MutableStateFlow("")
    val isSaving = MutableStateFlow(false)
    val buildStatus = MutableStateFlow<String?>(null) // "idle", "generating", "success", "error"
    val generatedProjectZip = MutableStateFlow<File?>(null)

    fun selectProject(project: Project?) {
        viewModelScope.launch {
            selectedProject.value = project
            selectedFile.value = null
            selectedFileContent.value = ""
            refreshFilesTree()
        }
    }

    fun refreshFilesTree() {
        val project = selectedProject.value ?: return
        val projectDir = repository.getProjectDir(project.id)
        filesTree.value = repository.getFilesTree(projectDir)
    }

    fun createProject(
        name: String,
        description: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        templateType: String = "Base Template",
        iconSymbol: String = "✨"
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val newProject = Project(
                id = id,
                name = name,
                description = description,
                packageName = packageName.ifBlank { "com.example.${name.lowercase().replace(" ", "")}" },
                versionName = versionName.ifBlank { "1.0" },
                versionCode = if (versionCode <= 0) 1 else versionCode,
                iconPath = iconSymbol
            )
            repository.insertProject(newProject)
            repository.createDefaultProjectFiles(newProject, templateType)
            selectProject(newProject)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            if (selectedProject.value?.id == project.id) {
                selectProject(null)
            }
            repository.deleteProject(project)
        }
    }

    fun updateProjectConfig(updatedProject: Project) {
        selectedProject.value = updatedProject
        viewModelScope.launch {
            repository.updateProject(updatedProject)
            refreshFilesTree()
        }
    }

    fun selectFile(file: File) {
        viewModelScope.launch {
            selectedFile.value = file
            if (isTextFile(file)) {
                val text = repository.readFileContent(file)
                selectedFileContent.value = text
            } else {
                selectedFileContent.value = "[Binary file: Use the asset tool or live preview to check components]"
            }
        }
    }

    fun saveCurrentFile(content: String) {
        val file = selectedFile.value ?: return
        if (!isTextFile(file)) return
        viewModelScope.launch {
            isSaving.value = true
            val success = repository.saveFileContent(file, content)
            if (success) {
                selectedFileContent.value = content
                // Also trigger dynamic config sync if edited file is config.json
                if (file.name == "config.json") {
                    syncProjectFromConfigFile(file)
                }
            }
            isSaving.value = false
        }
    }

    private suspend fun syncProjectFromConfigFile(file: File) {
        val content = repository.readFileContent(file)
        val map = MoshiHelper.fromJson(content) ?: return
        val current = selectedProject.value ?: return
        try {
            val permissionsMap = map["permissions"] as? Map<*, *>
            val updated = current.copy(
                name = map["name"] as? String ?: current.name,
                description = map["description"] as? String ?: current.description,
                packageName = map["packageName"] as? String ?: current.packageName,
                versionName = map["versionName"] as? String ?: current.versionName,
                versionCode = (map["versionCode"] as? Double)?.toInt() ?: current.versionCode,
                orientation = map["orientation"] as? String ?: current.orientation,
                themeColor = map["themeColor"] as? String ?: current.themeColor,
                useSplashScreen = map["useSplashScreen"] as? Boolean ?: current.useSplashScreen,
                homepage = map["homepage"] as? String ?: current.homepage,
                carryPhp = map["carryPhp"] as? Boolean ?: current.carryPhp,
                phpServerPort = (map["phpServerPort"] as? Double)?.toInt() ?: current.phpServerPort,
                splashImage = map["splashImage"] as? String ?: current.splashImage,
                iconPath = map["iconPath"] as? String ?: current.iconPath,
                fullscreenMode = map["fullscreenMode"] as? Boolean ?: current.fullscreenMode,
                hideTitleBar = map["hideTitleBar"] as? Boolean ?: current.hideTitleBar,
                allowLongPress = map["allowLongPress"] as? Boolean ?: current.allowLongPress,
                showLoadingUi = map["showLoadingUi"] as? Boolean ?: current.showLoadingUi,
                allowZoom = map["allowZoom"] as? Boolean ?: current.allowZoom,
                pcMode = map["pcMode"] as? Boolean ?: current.pcMode,
                allowMediaAutoplay = map["allowMediaAutoplay"] as? Boolean ?: current.allowMediaAutoplay,
                allowSwipingToRefresh = map["allowSwipingToRefresh"] as? Boolean ?: current.allowSwipingToRefresh,
                useVibration = permissionsMap?.get("vibration") as? Boolean ?: current.useVibration,
                useLocation = permissionsMap?.get("location") as? Boolean ?: current.useLocation,
                useCamera = permissionsMap?.get("camera") as? Boolean ?: current.useCamera,
                useMicrophone = permissionsMap?.get("microphone") as? Boolean ?: current.useMicrophone,
                useStorage = permissionsMap?.get("storage") as? Boolean ?: current.useStorage,
                useNotifications = permissionsMap?.get("notifications") as? Boolean ?: current.useNotifications,
                useBluetooth = permissionsMap?.get("bluetooth") as? Boolean ?: current.useBluetooth,
                useInternet = permissionsMap?.get("internet") as? Boolean ?: current.useInternet
            )
            repository.updateProject(updated)
            selectedProject.value = updated
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createNewFileInProject(fileName: String) {
        val project = selectedProject.value ?: return
        val projectDir = repository.getProjectDir(project.id)
        viewModelScope.launch {
            val created = repository.createNewFile(projectDir, fileName)
            if (created != null) {
                refreshFilesTree()
                selectFile(created)
            }
        }
    }

    fun createNewFolderInProject(folderName: String) {
        val project = selectedProject.value ?: return
        val projectDir = repository.getProjectDir(project.id)
        viewModelScope.launch {
            val created = repository.createNewFolder(projectDir, folderName)
            if (created != null) {
                refreshFilesTree()
            }
        }
    }

    fun deleteFileFromProject(file: File) {
        viewModelScope.launch {
            if (selectedFile.value?.absolutePath == file.absolutePath) {
                selectedFile.value = null
                selectedFileContent.value = ""
            }
            withContext(Dispatchers.IO) {
                if (file.exists()) file.deleteRecursively()
            }
            refreshFilesTree()
        }
    }

    fun renameFileInProject(file: File, newName: String) {
        viewModelScope.launch {
            val updated = repository.renameFile(file, newName)
            if (updated != null) {
                if (selectedFile.value?.absolutePath == file.absolutePath) {
                    selectFile(updated)
                }
                refreshFilesTree()
            }
        }
    }

    fun importAssetFile(assetUri: Uri, originalName: String) {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            val imported = repository.importAsset(project.id, assetUri, originalName)
            if (imported != null) {
                refreshFilesTree()
                selectFile(imported)
            }
        }
    }

    fun importZipArchive(zipUri: Uri, originalName: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val cleanName = originalName.substringBeforeLast(".zip").ifBlank { "Imported App" }
            val dummyProject = Project(
                id = id,
                name = cleanName,
                description = "Imported ZIP web framework project.",
                packageName = "com.aistudio.imported.${cleanName.lowercase().replace("[^a-z0-9]".toRegex(), "")}",
                versionName = "1.0",
                versionCode = 1
            )
            val success = repository.importProjectFromZip(getApplication(), zipUri, dummyProject)
            if (success) {
                repository.insertProject(dummyProject)
                selectProject(dummyProject)
            }
        }
    }

    fun createNewFileInFolder(fileName: String, targetFolder: File) {
        viewModelScope.launch {
            val created = repository.createNewFile(targetFolder, fileName)
            if (created != null) {
                refreshFilesTree()
            }
        }
    }

    fun createNewFolderInFolder(folderName: String, targetFolder: File) {
        viewModelScope.launch {
            val created = repository.createNewFolder(targetFolder, folderName)
            if (created != null) {
                refreshFilesTree()
            }
        }
    }

    fun importFileToTargetFolder(uri: Uri, fileName: String, targetFolder: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (!targetFolder.exists()) targetFolder.mkdirs()
                    val targetFile = File(targetFolder, fileName)
                    getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            refreshFilesTree()
        }
    }

    fun buildNativeProject() {
        val project = selectedProject.value ?: return
        viewModelScope.launch {
            buildStatus.value = "generating"
            val outputDir = File(getApplication<Application>().cacheDir, "builds").apply { mkdirs() }
            val zipFile = File(outputDir, "${project.name.replace(" ", "")}_SourceWorkspace.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }

            val success = projectGenerator.generateCompleteProjectZip(project, zipFile)
            if (success) {
                generatedProjectZip.value = zipFile
                buildStatus.value = "success"
            } else {
                buildStatus.value = "error"
            }
        }
    }

    fun dismissBuildStatus() {
        buildStatus.value = null
    }

    fun isTextFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("html", "css", "js", "json", "txt", "xml", "svg")
    }
}

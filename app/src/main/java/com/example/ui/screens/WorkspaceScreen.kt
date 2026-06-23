package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.FileNode
import com.example.data.Project
import com.example.ui.ProjectViewModel
import java.io.File
import android.widget.Toast
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    currentProject: Project,
    viewModel: ProjectViewModel,
    onBackToProjects: () -> Unit
) {
    val context = LocalContext.current
    
    // Core states
    val filesList by viewModel.filesTree.collectAsState()
    val openedFile by viewModel.selectedFile.collectAsState()
    val openedFileContent by viewModel.selectedFileContent.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val buildStatus by viewModel.buildStatus.collectAsState()
    val generatedProjectZip by viewModel.generatedProjectZip.collectAsState()

    // Preview
    var showLivePreview by remember { mutableStateOf(false) }

    // Navigation Folder Path (Relative to project root folder)
    var currentFolderPath by remember { mutableStateOf("") }
    
    val projectRootDir = remember(currentProject.id) {
        File(context.filesDir, "projects/${currentProject.id}")
    }
    val currentFolder = remember(projectRootDir, currentFolderPath) {
        if (currentFolderPath.isBlank()) projectRootDir else File(projectRootDir, currentFolderPath)
    }

    // Refresh tree on load
    LaunchedEffect(currentProject.id) {
        viewModel.refreshFilesTree()
    }

    // Direct Importer for any file/asset into current folder
    val documentImporterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val resolver = context.contentResolver
                var name = "imported_file"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(idx)
                    }
                }
                viewModel.importFileToTargetFolder(uri, name, currentFolder)
            }
        }
    }

    // Create dialogs state
    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var inputCreatedName by remember { mutableStateOf("") }

    // Publish App Compiler parameters panel dialog state
    var showPublishPanel by remember { mutableStateOf(false) }

    // Search query states
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQueryText by remember { mutableStateOf("") }

    // Filter current viewed files list based on relative folder path and search query
    val filesInCurrentFolder = remember(filesList, currentFolderPath, searchQueryText) {
        val list = currentFolder.listFiles() ?: emptyArray()
        val sortedList = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).toList()
        if (searchQueryText.isBlank()) {
            sortedList
        } else {
            sortedList.filter { it.name.contains(searchQueryText, ignoreCase = true) }
        }
    }

    if (showLivePreview) {
        // Resolve target live HTML file. Preview the active open file if HTML, otherwise default index.html
        val targetHtmlFile = if (openedFile != null && openedFile!!.extension.lowercase() == "html") {
            openedFile!!
        } else {
            val homepageName = currentProject.homepage.ifBlank { "index.html" }
            File(projectRootDir, homepageName)
        }

        LivePreviewScreen(
            indexFile = targetHtmlFile,
            onClose = { showLivePreview = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentProject.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (currentFolderPath.isBlank()) "Root Level: Workspace/" else "Workspace/$currentFolderPath",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToProjects) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Instant Dynamic HTML Preview button
                        Button(
                            onClick = { showLivePreview = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Preview", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Code Editor Overlay Panel
                if (openedFile != null && openedFileContent.isNotBlank()) {
                    CodeWorkspacePanel(
                        file = openedFile!!,
                        initialContent = openedFileContent,
                        isSaving = isSaving,
                        onSave = { updatedCode ->
                            viewModel.saveCurrentFile(updatedCode)
                        },
                        onClose = {
                            viewModel.selectedFile.value = null
                            viewModel.selectedFileContent.value = ""
                            viewModel.refreshFilesTree()
                        }
                    )
                } else {
                    // Unified Workspace Screen Scroll Column
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. High contrast ORANGE Publish Card
                        PublishCard(onClick = { showPublishPanel = true })

                        // 2. Folder explorer header (Website / assets / ...)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFC471ED))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Website Files",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Directory Action Controls Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Search toggle
                                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.secondary)
                                }

                                // Navigate Parent Folder (Up directory button)
                                if (currentFolderPath.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val parts = currentFolderPath.split("/")
                                            currentFolderPath = if (parts.size <= 1) "" else parts.dropLast(1).joinToString("/")
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Up Folder", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // Create New subfolder/files trigger
                                Box {
                                    var showAddMenu by remember { mutableStateOf(false) }
                                    IconButton(onClick = { showAddMenu = true }) {
                                        Icon(Icons.Default.AddBox, contentDescription = "Add Content", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Create New File") },
                                            leadingIcon = { Icon(Icons.Default.Code, null) },
                                            onClick = {
                                                showAddMenu = false
                                                isCreatingFolder = false
                                                showCreateDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Create New Folder") },
                                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                            onClick = {
                                                showAddMenu = false
                                                isCreatingFolder = true
                                                showCreateDialog = true
                                            }
                                        )
                                    }
                                }

                                // Direct Import files button (copies straight to current reviewed folder)
                                Button(
                                    onClick = { documentImporterLauncher.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Import Items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Search Bar block (displayed if triggered)
                        if (isSearchVisible) {
                            OutlinedTextField(
                                value = searchQueryText,
                                onValueChange = { searchQueryText = it },
                                placeholder = { Text("Filter current directory files...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.FilterList, null) },
                                trailingIcon = {
                                    if (searchQueryText.isNotBlank()) {
                                        IconButton(onClick = { searchQueryText = "" }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    }
                                }
                            )
                        }

                        // Display Breadcrumb path trace clicks
                        BreadcrumbRow(
                            currentPath = currentFolderPath,
                            onPathSegmentSelected = { segmentIndex ->
                                if (segmentIndex == -1) {
                                    currentFolderPath = ""
                                } else {
                                    val parts = currentFolderPath.split("/")
                                    currentFolderPath = parts.take(segmentIndex + 1).joinToString("/")
                                }
                            }
                        )

                        // 3. Flat listing of files and folders inside currentFolder
                        if (filesInCurrentFolder.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color.LightGray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("This directory folder is empty.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    Text("Tap '+' or 'Import items' to build files here.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filesInCurrentFolder) { file ->
                                    WorkspaceFileSystemRow(
                                        file = file,
                                        protectedHomepage = currentProject.homepage.ifBlank { "index.html" },
                                        onFolderClick = { folderName ->
                                            currentFolderPath = if (currentFolderPath.isBlank()) folderName else "$currentFolderPath/$folderName"
                                        },
                                        onFileClick = {
                                            viewModel.selectFile(file)
                                        },
                                        onDeleteClick = {
                                            viewModel.deleteFileFromProject(file)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom folder-sensitive creation dialogue
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; inputCreatedName = "" },
            title = { Text(if (isCreatingFolder) "Create Directory" else "Create Text File") },
            text = {
                OutlinedTextField(
                    value = inputCreatedName,
                    onValueChange = { inputCreatedName = it },
                    label = { Text(if (isCreatingFolder) "Folder Name (e.g. assets)" else "File Name (e.g. contact.html)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputCreatedName.isNotBlank()) {
                            if (isCreatingFolder) {
                                viewModel.createNewFolderInFolder(inputCreatedName, currentFolder)
                            } else {
                                viewModel.createNewFileInFolder(inputCreatedName, currentFolder)
                            }
                        }
                        showCreateDialog = false
                        inputCreatedName = ""
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; inputCreatedName = "" }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Publish App Configuration Parameters dialog (Mirroring Image 3)
    if (showPublishPanel) {
        PublishAppPanelDialog(
            project = currentProject,
            buildStatus = buildStatus,
            zipFile = generatedProjectZip,
            viewModel = viewModel,
            onDismiss = {
                showPublishPanel = false
                viewModel.dismissBuildStatus()
            },
            onSaveAndCompile = { updatedConfig ->
                viewModel.updateProjectConfig(updatedConfig)
                viewModel.buildNativeProject()
            }
        )
    }
}

// Vibrant Orange Publish Card Composable
@Composable
fun PublishCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONVERT TO ANDROID APP",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure package IDs, custom haptics, autoplay permissions, and compile your work into production-ready Android zip.",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Interactive Navigation Breadcrumbs Composable
@Composable
fun BreadcrumbRow(
    currentPath: String,
    onPathSegmentSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Website",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onPathSegmentSelected(-1) }
        )

        if (currentPath.isNotBlank()) {
            val parts = currentPath.split("/")
            parts.forEachIndexed { idx, segment ->
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (idx == parts.lastIndex) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (idx == parts.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onPathSegmentSelected(idx) }
                )
            }
        }
    }
}

// Flat Directory item row layout
@Composable
fun WorkspaceFileSystemRow(
    file: File,
    protectedHomepage: String = "index.html",
    onFolderClick: (String) -> Unit,
    onFileClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDirectory = file.isDirectory
    val ext = file.extension.lowercase()
    
    val icon = when {
        isDirectory -> Icons.Default.Folder
        ext in listOf("html", "xml") -> Icons.Default.Html
        ext == "css" -> Icons.Default.Css
        ext == "js" -> Icons.Default.Javascript
        ext == "json" -> Icons.Default.Settings
        ext in listOf("png", "jpg", "jpeg", "webp") -> Icons.Default.Image
        ext in listOf("mp3", "wav", "ogg") -> Icons.Default.AudioFile
        else -> Icons.Default.Description
    }

    val iconColor = when {
        isDirectory -> Color(0xFFFFA000) // Folder gold
        ext == "html" -> Color(0xFFFF5722) // HTML orange
        ext == "css" -> Color(0xFF2196F3) // CSS blue
        ext == "js" -> Color(0xFFFFEB3B) // JS yellow
        ext == "json" -> Color(0xFF4CAF50) // JSON green
        ext in listOf("png", "jpg", "jpeg", "webp") -> Color(0xFF00bcd4)
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isDirectory) {
                    onFolderClick(file.name)
                } else {
                    onFileClick()
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isDirectory) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDirectory) "Directory" else "${ext.uppercase()} File • ${formatFileSize(file.length())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            // Bug #11 fix: protect config.json + actual homepage (from project settings, not hardcoded)
            val protectedFiles = setOf("config.json", protectedHomepage)
            if (file.name !in protectedFiles) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    return "$mb MB"
}

// Full popup compiler dialog matching Image 3 parameters
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishAppPanelDialog(
    project: Project,
    buildStatus: String?,
    zipFile: File?,
    viewModel: ProjectViewModel,
    onDismiss: () -> Unit,
    onSaveAndCompile: (Project) -> Unit
) {
    val context = LocalContext.current
    
    var appName by remember { mutableStateOf(project.name) }
    var desc by remember { mutableStateOf(project.description) }
    var packageId by remember { mutableStateOf(project.packageName) }
    var hoverHomepage by remember { mutableStateOf(project.homepage.ifBlank { "index.html" }) }
    var verName by remember { mutableStateOf(project.versionName) }
    var verCode by remember { mutableStateOf(project.versionCode.toString()) }
    var selectedOrientation by remember { mutableStateOf(project.orientation) }
    var splashScreenOn by remember { mutableStateOf(project.useSplashScreen) }
    var carryIconPath by remember { mutableStateOf(project.iconPath) }

    // Scan standard files in the project folder for available images
    val projectWebRootDir = remember(project.id) {
        File(context.filesDir, "projects/${project.id}")
    }
    val projectImageOptions = remember(projectWebRootDir) {
        val list = mutableListOf<String>()
        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scan(file)
                } else if (file.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")) {
                    val rel = file.absolutePath.substringAfter(projectWebRootDir.absolutePath + "/")
                    list.add(rel)
                }
            }
        }
        try {
            if (projectWebRootDir.exists()) {
                scan(projectWebRootDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    // Android wrapper system features
    var carryFullscreen by remember { mutableStateOf(project.fullscreenMode) }
    var carryHideTitle by remember { mutableStateOf(project.hideTitleBar) }
    var carryLoadingUi by remember { mutableStateOf(project.showLoadingUi) }
    var carryZoomGesture by remember { mutableStateOf(project.allowZoom) }
    var carryMediaAutoplay by remember { mutableStateOf(project.allowMediaAutoplay) }
    var carrySwipeRefresh by remember { mutableStateOf(project.allowSwipingToRefresh) }
    var carryPcMode by remember { mutableStateOf(project.pcMode) }
    var carryLongPress by remember { mutableStateOf(project.allowLongPress) }

    // Permissions toggles
    var useVibration by remember { mutableStateOf(project.useVibration) }
    var useLocation by remember { mutableStateOf(project.useLocation) }
    var useCamera by remember { mutableStateOf(project.useCamera) }
    var useMicrophone by remember { mutableStateOf(project.useMicrophone) }
    var useStorage by remember { mutableStateOf(project.useStorage) }
    var useNotifications by remember { mutableStateOf(project.useNotifications) }
    var useBluetooth by remember { mutableStateOf(project.useBluetooth) }
    var useInternet by remember { mutableStateOf(project.useInternet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AppRegistration,
                        contentDescription = null,
                        tint = Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Build Native App",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Convert your offline website package into a fully-packaged, buildable Android dynamic app wrap. Set customized system permissions and wrap interfaces below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Divider()

                // Basic Package ID Metadata
                Text(
                    text = "App Profile Config",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Launcher Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = packageId,
                    onValueChange = { packageId = it },
                    label = { Text("Android Package ID (com.example.app)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hoverHomepage,
                    onValueChange = { hoverHomepage = it },
                    label = { Text("Homepage Entry Path (index.html)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Custom App Icon Selector
                var iconDropdownExpanded by remember { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "App Launcher Icon",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (carryIconPath.isBlank()) "(Default fallback branded icon)" else carryIconPath,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Project App Icon") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                if (carryIconPath.isNotBlank()) {
                                    val imgFile = remember(carryIconPath) { File(projectWebRootDir, carryIconPath) }
                                    if (imgFile.exists()) {
                                        AsyncImage(
                                            model = imgFile,
                                            contentDescription = "App Icon Preview",
                                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Android, null)
                                    }
                                } else {
                                    Icon(Icons.Default.Android, null)
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { iconDropdownExpanded = !iconDropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select App Icon")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = iconDropdownExpanded,
                            onDismissRequest = { iconDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("(Default fallback branded icon)") },
                                leadingIcon = { Icon(Icons.Default.Android, null) },
                                onClick = {
                                    carryIconPath = ""
                                    iconDropdownExpanded = false
                                }
                            )
                            if (projectImageOptions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No project images found (add a PNG/JPG)") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                projectImageOptions.forEach { imgPath ->
                                    val imgFile = remember(imgPath) { File(projectWebRootDir, imgPath) }
                                    DropdownMenuItem(
                                        text = { Text(imgPath) },
                                        leadingIcon = {
                                            if (imgFile.exists()) {
                                                AsyncImage(
                                                    model = imgFile,
                                                    contentDescription = "Project image option",
                                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(Icons.Default.Image, null, tint = Color(0xFF00BCD4))
                                            }
                                        },
                                        onClick = {
                                            carryIconPath = imgPath
                                            iconDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = verName,
                        onValueChange = { verName = it },
                        label = { Text("Version Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = verCode,
                        onValueChange = { verCode = it },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Divider()

                // Rotation Preferenced Mode
                Text(
                    text = "Layout & Rotation Mode",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("unspecified", "portrait", "landscape").forEach { opt ->
                        FilterChip(
                            selected = selectedOrientation == opt,
                            onClick = { selectedOrientation = opt },
                            label = { Text(opt.uppercase(), fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { splashScreenOn = !splashScreenOn }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = splashScreenOn, onCheckedChange = { splashScreenOn = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Incorporate startup splash image delay screen", style = MaterialTheme.typography.bodyMedium)
                }

                Divider()

                // Display options
                Text(
                    text = "Native UI WebView Preferences",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    PermissionCheckbox("Enable Fullscreen Immersive Mode", carryFullscreen) { carryFullscreen = it }
                    PermissionCheckbox("Hide System Android TitleBar", carryHideTitle) { carryHideTitle = it }
                    PermissionCheckbox("Display dynamic Loading progress overlay", carryLoadingUi) { carryLoadingUi = it }
                    PermissionCheckbox("Allow pinch zoom gestures in WebView", carryZoomGesture) { carryZoomGesture = it }
                    PermissionCheckbox("Allow media autoplay without tap user interaction", carryMediaAutoplay) { carryMediaAutoplay = it }
                    PermissionCheckbox("Allow Swiping down to Refresh page", carrySwipeRefresh) { carrySwipeRefresh = it }
                    PermissionCheckbox("Override user agent to PC Desktop mode", carryPcMode) { carryPcMode = it }
                    PermissionCheckbox("Allow long press word selection context", carryLongPress) { carryLongPress = it }
                }

                Divider()

                // Permissions Section
                Text(
                    text = "System SDK Permissions",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9800)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    PermissionCheckbox("Device Vibration Feedback (Haptics)", useVibration) { useVibration = it }
                    PermissionCheckbox("GPS Fine/Coarse Location tracking", useLocation) { useLocation = it }
                    PermissionCheckbox("Local Storage StorageAccess read/write", useStorage) { useStorage = it }
                    PermissionCheckbox("Camera Stream photo uploads", useCamera) { useCamera = it }
                    PermissionCheckbox("Microphone sensor audio inputs", useMicrophone) { useMicrophone = it }
                    PermissionCheckbox("Push Notifications (Post-Notify Android 13+)", useNotifications) { useNotifications = it }
                    PermissionCheckbox("Bluetooth Core connectivity channels", useBluetooth) { useBluetooth = it }
                    PermissionCheckbox("Device Internet connections pipeline", useInternet) { useInternet = it }
                }

                // If compiling builds, display gorgeous status
                if (buildStatus != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (buildStatus) {
                                "success" -> Color(0xFFE8F5E9)
                                "error" -> Color(0xFFFFEBEE)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when (buildStatus) {
                                "generating" -> {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color(0xFFFF9800))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Writing Kotlin Android wrapper, injecting bridge and ZIP compiling files...", fontSize = 12.sp, color = Color.Black, textAlign = TextAlign.Center)
                                }
                                "success" -> {
                                    Icon(Icons.Default.CheckCircle, "Done", tint = Color(0xFF2E7D32), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("APP GENERATION COMPLETED PERFECTLY!", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Save to Device Downloads Button
                                        Button(
                                            onClick = {
                                                if (zipFile != null && zipFile.exists()) {
                                                    val success = saveZipToDownloads(context, zipFile)
                                                    if (success) {
                                                        Toast.makeText(context, "Saved to your device's Downloads folder!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to save file.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download ZIP")
                                        }

                                        // Share Button
                                        Button(
                                            onClick = {
                                                if (zipFile != null && zipFile.exists()) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        zipFile
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/zip"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Project Wrapper ZIP"))
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                                "error" -> {
                                    Icon(Icons.Default.Error, "Fail", tint = Color(0xFFC62828), modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Wrappers generation failed. Ensure details are correct.", color = Color(0xFFC62828), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val codeNum = verCode.toIntOrNull() ?: 1
                    val newObj = project.copy(
                        name = appName,
                        description = desc,
                        packageName = packageId,
                        homepage = hoverHomepage,
                        versionName = verName,
                        versionCode = codeNum,
                        orientation = selectedOrientation,
                        useSplashScreen = splashScreenOn,
                        fullscreenMode = carryFullscreen,
                        hideTitleBar = carryHideTitle,
                        showLoadingUi = carryLoadingUi,
                        allowZoom = carryZoomGesture,
                        allowMediaAutoplay = carryMediaAutoplay,
                        allowSwipingToRefresh = carrySwipeRefresh,
                        pcMode = carryPcMode,
                        allowLongPress = carryLongPress,
                        useVibration = useVibration,
                        useLocation = useLocation,
                        useStorage = useStorage,
                        useCamera = useCamera,
                        useMicrophone = useMicrophone,
                        useNotifications = useNotifications,
                        useBluetooth = useBluetooth,
                        useInternet = useInternet,
                        iconPath = carryIconPath
                    )
                    onSaveAndCompile(newObj)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("COMPILE ANDROID APPLICATION", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun PermissionCheckbox(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}

// Save generated wrapper ZIP to external storage / Public Downloads folder using modern MediaStore APIs
private fun saveZipToDownloads(context: android.content.Context, zipFile: File): Boolean {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, zipFile.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { out ->
                    if (out != null) {
                        java.io.FileInputStream(zipFile).use { input ->
                            input.copyTo(out)
                        }
                        return true
                    }
                }
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val targetFile = File(downloadsDir, zipFile.name)
            zipFile.copyTo(targetFile, overwrite = true)
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}


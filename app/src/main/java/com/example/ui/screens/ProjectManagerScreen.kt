package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import java.io.File
import com.example.data.Project
import com.example.ui.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerScreen(
    viewModel: ProjectViewModel,
    onProjectSelected: (Project) -> Unit
) {
    val context = LocalContext.current
    val projectsList by viewModel.projects.collectAsState()

    // Dialog flags
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    // Dialog input values
    var inputName by remember { mutableStateOf("") }
    var inputPackage by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }
    var inputVersionName by remember { mutableStateOf("1.0") }
    var inputVersionCode by remember { mutableStateOf("1") }

    // Renamer input
    var renameValue by remember { mutableStateOf("") }

    // Zip Picker SAF launcher
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            var fileName = "imported_project.zip"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            viewModel.importZipArchive(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WebApp Builder",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Develop hybrid Android apps offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { zipPickerLauncher.launch("application/zip") }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import ZIP Project")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, "Add Project") },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Creative Visual Header Gradient Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(16.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.LaptopMac,
                        contentDescription = "Builder Banner",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Compile Web Assets Locally",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Edit HTML/CSS/JS with full native API bridges.",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Project Listing
            if (projectsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = "Empty Projects List",
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Projects Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Click 'New Project' below or import an existing bundle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(projectsList) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { onProjectSelected(project) },
                            onRenameClick = {
                                projectToRename = project
                                renameValue = project.name
                                showRenameDialog = true
                            },
                            onDeleteClick = {
                                projectToDelete = project
                            },
                            onDuplicateClick = {
                                viewModel.createProject(
                                    name = "${project.name} Copy",
                                    description = project.description,
                                    packageName = "${project.packageName}.copy",
                                    versionName = project.versionName,
                                    versionCode = project.versionCode + 1
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // New Project Dialog matching Image 1 UI
    var selectedIconSymbol by remember { mutableStateOf("✨") }
    var selectedTemplate by remember { mutableStateOf("Base Template") }
    var showAdvancedParams by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { 
                Text(
                    text = "Create Project",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Website Icon Selection Header Section
                    Text(
                        text = "Website Icon",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(selectedIconSymbol, fontSize = 36.sp)
                    }
                    
                    // Simple Icon Quick Picker symbols row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf("✨", "🌐", "🚀", "📱", "🎯", "🛒", "📝", "💖", "🏰").forEach { symbol ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (selectedIconSymbol == symbol) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (selectedIconSymbol == symbol) 2.dp else 1.dp,
                                        color = if (selectedIconSymbol == symbol) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedIconSymbol = symbol },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symbol, fontSize = 16.sp)
                            }
                        }
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.2f))

                    // Project Name Input field
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = {
                            inputName = it
                            if (inputPackage.isBlank() || inputPackage.startsWith("com.example.")) {
                                val clean = it.lowercase().replace("[^a-z0-9]".toRegex(), "")
                                inputPackage = "com.example.$clean"
                            }
                        },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Advanced parameters toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedParams = !showAdvancedParams }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showAdvancedParams) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Advanced",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show Advanced Parameters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }

                    if (showAdvancedParams) {
                        OutlinedTextField(
                            value = inputDesc,
                            onValueChange = { inputDesc = it },
                            label = { Text("App Workspace Description") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputPackage,
                            onValueChange = { inputPackage = it },
                            label = { Text("Android Package ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inputVersionName,
                                onValueChange = { inputVersionName = it },
                                label = { Text("Version Label") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputVersionCode,
                                onValueChange = { inputVersionCode = it },
                                label = { Text("Code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Divider(color = Color.LightGray.copy(alpha = 0.2f))

                    // Templates Carousel
                    Text(
                        text = "Template",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Base Template", "Single Page App").forEach { template ->
                            val isSelected = selectedTemplate == template
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTemplate = template }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Box(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                    Column {
                                        Text(
                                            text = template,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (template == "Base Template") "Default static HTML template" else "Sleek responsive tabbed app layout",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray,
                                            maxLines = 2
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.TopEnd)
                                        )
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
                        if (inputName.isNotBlank()) {
                            val code = inputVersionCode.toIntOrNull() ?: 1
                            viewModel.createProject(
                                name = inputName,
                                description = inputDesc,
                                packageName = inputPackage,
                                versionName = inputVersionName,
                                versionCode = code,
                                templateType = selectedTemplate,
                                iconSymbol = selectedIconSymbol
                            )
                            showCreateDialog = false
                            // reset fields
                            inputName = ""
                            inputDesc = ""
                            inputPackage = ""
                            inputVersionName = "1.0"
                            inputVersionCode = "1"
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Import Project trigger exactly placed at bottom-left corner of choices
                    TextButton(
                        onClick = {
                            showCreateDialog = false
                            zipPickerLauncher.launch("application/zip")
                        }
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORT PROJECT")
                    }

                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("CANCEL")
                    }
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && projectToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            viewModel.updateProjectConfig(projectToRename!!.copy(name = renameValue))
                        }
                        showRenameDialog = false
                        projectToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; projectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project Workspace?") },
            text = {
                Text("Are you sure you want to permanently delete '${projectToDelete!!.name}'? All html/css/js sources and custom assets will be erased permanently from device storage.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteProject(projectToDelete!!)
                        projectToDelete = null
                    }
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDuplicateClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Custom aesthetic workspace badge icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val context = LocalContext.current
                        val imageFile = remember(project.id, project.iconPath) {
                            if (project.iconPath.isNotBlank()) {
                                File(File(context.filesDir, "projects/${project.id}"), project.iconPath)
                            } else null
                        }

                        if (imageFile != null && imageFile.exists()) {
                            AsyncImage(
                                model = imageFile,
                                contentDescription = "Project logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else if (project.iconPath.isNotBlank()) {
                            Text(project.iconPath, fontSize = 20.sp)
                        } else {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Project logo",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = project.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Project") },
                            onClick = { showMenu = false; onClick() },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, "Open") }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { showMenu = false; onRenameClick() },
                            leadingIcon = { Icon(Icons.Default.Edit, "Rename") }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Project") },
                            onClick = { showMenu = false; onDuplicateClick() },
                            leadingIcon = { Icon(Icons.Default.FileCopy, "Duplicate") }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Delete Workspace") },
                            onClick = { showMenu = false; onDeleteClick() },
                            leadingIcon = { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = project.description.ifBlank { "No description added yet." },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("V${project.versionName} (${project.versionCode})") }
                )

                Text(
                    text = "Theme: ${project.themeColor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

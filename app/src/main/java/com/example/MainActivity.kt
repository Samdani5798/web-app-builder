package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ProjectViewModel
import com.example.ui.screens.ProjectManagerScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // ── Bug #13 fix: actually handle permission results, log denials ──
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionResults ->
        val denied = permissionResults.filterValues { !it }.keys
        val granted = permissionResults.filterValues { it }.keys

        if (granted.isNotEmpty()) {
            Log.d("Permissions", "Granted: ${granted.joinToString { it.substringAfterLast(".") }}")
        }

        if (denied.isNotEmpty()) {
            Log.w("Permissions", "Denied: ${denied.joinToString { it.substringAfterLast(".") }}")

            // Notify user which features will not work
            val affected = denied.mapNotNull { perm ->
                when {
                    perm.contains("CAMERA")            -> "Camera"
                    perm.contains("RECORD_AUDIO")      -> "Microphone"
                    perm.contains("LOCATION")          -> "Location"
                    perm.contains("POST_NOTIFICATION") -> "Notifications"
                    perm.contains("READ_MEDIA")        -> "Gallery"
                    perm.contains("READ_EXTERNAL")     -> "Gallery"
                    perm.contains("BLUETOOTH")         -> "Bluetooth"
                    else -> null
                }
            }.distinct()

            if (affected.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Some features disabled: ${affected.joinToString(", ")}.\nGo to App Settings to enable.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )

        // POST_NOTIFICATIONS requires API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Legacy storage permissions for API < 30
            @Suppress("DEPRECATION")
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // BLUETOOTH_CONNECT requires API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Only request permissions that are not already granted
        val notGranted = permissionsToRequest.filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }

        try {
            if (notGranted.isNotEmpty()) {
                requestMultiplePermissionsLauncher.launch(notGranted.toTypedArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: ProjectViewModel = viewModel()
                    val selectedProject by viewModel.selectedProject.collectAsState()

                    if (selectedProject == null) {
                        ProjectManagerScreen(
                            viewModel = viewModel,
                            onProjectSelected = { project ->
                                viewModel.selectProject(project)
                            }
                        )
                    } else {
                        WorkspaceScreen(
                            currentProject = selectedProject!!,
                            viewModel = viewModel,
                            onBackToProjects = {
                                viewModel.selectProject(null)
                            }
                        )
                    }
                }
            }
        }
    }
}

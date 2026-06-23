package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.core.WebAppNativeBridge
import java.io.File

data class ConsoleLog(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val sourceId: String,
    val lineNumber: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePreviewScreen(
    indexFile: File,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isBridgeConnected by remember { mutableStateOf(false) }

    // ── Bug #1 fix: real camera launcher using ACTION_IMAGE_CAPTURE ──
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            // Inject result back into JS if needed
            webViewInstance?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('nativeCameraResult', { detail: { uri: '${cameraImageUri}' } }))",
                null
            )
        }
        cameraImageUri = null
    }

    fun launchCamera() {
        try {
            val photoFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── File picker / gallery launcher ──
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileChooserCallback
        fileChooserCallback = null
        if (result.resultCode == android.app.Activity.RESULT_OK && cb != null) {
            val data = result.data
            val results: Array<Uri>? = when {
                data?.clipData != null -> Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
                data?.dataString != null -> arrayOf(Uri.parse(data.dataString))
                else -> null
            }
            cb.onReceiveValue(results)
        } else {
            cb?.onReceiveValue(null)
        }
    }

    // ── Bug #4 fix: WebRTC permission handler — grant even when already granted ──
    var pendingWebViewPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val request = pendingWebViewPermissionRequest ?: return@rememberLauncherForActivityResult
        pendingWebViewPermissionRequest = null
        val toGrant = request.resources.filter { res ->
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> grantMap[android.Manifest.permission.CAMERA] == true
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> grantMap[android.Manifest.permission.RECORD_AUDIO] == true
                else -> true
            }
        }
        if (toGrant.isNotEmpty()) request.grant(toGrant.toTypedArray()) else request.deny()
    }

    var pageTitle by remember { mutableStateOf("Live Preview") }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isConsoleOpen by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<ConsoleLog>() }
    val listState = rememberLazyListState()

    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) listState.animateScrollToItem(consoleLogs.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = pageTitle, style = MaterialTheme.typography.titleMedium)
                        // ── Bug #8 fix: show real progress; Bug #15 fix: only show connected after bridge inject ──
                        when {
                            loadProgress in 1..99 -> LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            isBridgeConnected -> Text(
                                text = "Bridge connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50)
                            )
                            else -> Text(
                                text = "Loading…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        webViewInstance?.reload()
                        isBridgeConnected = false
                        consoleLogs.add(ConsoleLog("User triggered manual reload.", ConsoleMessage.MessageLevel.LOG, "Container", 0))
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    Box {
                        IconButton(onClick = { isConsoleOpen = !isConsoleOpen }) {
                            BadgedBox(badge = {
                                val errorCount = consoleLogs.count { it.level == ConsoleMessage.MessageLevel.ERROR }
                                if (errorCount > 0) Badge { Text(errorCount.toString()) }
                            }) {
                                Icon(Icons.Default.Terminal, contentDescription = "Console Logs")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setGeolocationEnabled(true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    // Bug #8 fix: don't hardcode 10 — let onProgressChanged drive it
                                    isBridgeConnected = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loadProgress = 100
                                    pageTitle = view?.title ?: "Live Preview"
                                    // Confirm bridge is injected after page load
                                    view?.evaluateJavascript(
                                        "(typeof window.Android !== 'undefined' && typeof window.Android.showToast === 'function')"
                                    ) { result ->
                                        isBridgeConnected = result?.trim() == "true"
                                    }
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        consoleLogs.add(ConsoleLog(
                                            "Network Error: ${error?.description}. URL: ${request?.url}",
                                            ConsoleMessage.MessageLevel.ERROR, "Network", 0
                                        ))
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                // Bug #8 fix: real progress from WebChromeClient
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: android.webkit.GeolocationPermissions.Callback?
                                ) {
                                    callback?.invoke(origin, true, false)
                                }

                                // ── Bug #4 fix: grant if already have permission, request only if missing ──
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    if (request == null) return
                                    val needToRequest = mutableListOf<String>()
                                    val canGrantNow = mutableListOf<String>()

                                    for (res in request.resources) {
                                        when (res) {
                                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                                    canGrantNow.add(res)
                                                } else {
                                                    needToRequest.add(android.Manifest.permission.CAMERA)
                                                }
                                            }
                                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    canGrantNow.add(res)
                                                } else {
                                                    needToRequest.add(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                            else -> canGrantNow.add(res)
                                        }
                                    }

                                    if (needToRequest.isNotEmpty()) {
                                        pendingWebViewPermissionRequest = request
                                        permissionLauncher.launch(needToRequest.toTypedArray())
                                    } else {
                                        // All permissions already granted — grant immediately
                                        request.grant(request.resources)
                                    }
                                }

                                // ── Bug #5 fix: detect capture hint and launch camera directly ──
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    // Bug #12 fix: null out previous callback safely before assigning new
                                    fileChooserCallback?.onReceiveValue(null)
                                    fileChooserCallback = filePathCallback

                                    val isCaptureMode = fileChooserParams?.isCaptureEnabled == true
                                    val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
                                    val isImageOnly = acceptTypes.any { it.contains("image") }

                                    if (isCaptureMode && isImageOnly) {
                                        // Launch native camera directly
                                        try {
                                            val photoFile = File(context.cacheDir, "file_chooser_cam_${System.currentTimeMillis()}.jpg")
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                                            // Return URI via MediaStore intent so callback gets it
                                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                                            }
                                            pickFilesLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            fileChooserCallback?.onReceiveValue(null)
                                            fileChooserCallback = null
                                        }
                                        return true
                                    }

                                    // Normal gallery / file picker
                                    val intent = fileChooserParams?.createIntent()
                                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            type = "*/*"
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                    try {
                                        pickFilesLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        fileChooserCallback?.onReceiveValue(null)
                                        fileChooserCallback = null
                                        return false
                                    }
                                    return true
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    if (consoleMessage != null) {
                                        consoleLogs.add(ConsoleLog(
                                            message = consoleMessage.message(),
                                            level = consoleMessage.messageLevel(),
                                            sourceId = consoleMessage.sourceId().substringAfterLast("/"),
                                            lineNumber = consoleMessage.lineNumber()
                                        ))
                                    }
                                    return true
                                }
                            }

                            // ── Bug #1 fix: real camera intent in the callback ──
                            val nativeBridge = WebAppNativeBridge(context) {
                                launchCamera()
                            }
                            addJavascriptInterface(nativeBridge, "AndroidBridge")
                            addJavascriptInterface(nativeBridge, "Android")

                            loadUrl("file://${indexFile.absolutePath}")
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── Bug #14 fix: "LOGS" not "LOOPS" ──
            AnimatedVisibility(visible = isConsoleOpen, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier.fillMaxWidth().height(240.dp).background(Color(0xFF151515))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF222222))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEVELOPER CONSOLE LOGS",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF00FF00)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { consoleLogs.clear() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { isConsoleOpen = false }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close console", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (consoleLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Console is ready. Bridge injected.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(consoleLogs) { log -> LogLineItem(log = log) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogLineItem(log: ConsoleLog) {
    val logColor = when (log.level) {
        ConsoleMessage.MessageLevel.ERROR   -> Color(0xFFFF5555)
        ConsoleMessage.MessageLevel.WARNING -> Color(0xFFFFCC00)
        ConsoleMessage.MessageLevel.TIP     -> Color(0xFF00AAFF)
        else                                -> Color(0xFF00FF00)
    }
    val badgeText = when (log.level) {
        ConsoleMessage.MessageLevel.ERROR   -> "ERROR"
        ConsoleMessage.MessageLevel.WARNING -> "WARN"
        ConsoleMessage.MessageLevel.TIP     -> "TIP"
        else                                -> "LOG"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = "[$badgeText]", color = logColor, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = log.message, color = Color.White, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text(text = "${log.sourceId}:${log.lineNumber}", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

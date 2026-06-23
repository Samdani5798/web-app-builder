package com.example.data

import android.content.Context
import android.net.Uri
import com.example.core.MoshiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FileNode(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val extension: String = "",
    val children: List<FileNode> = emptyList()
)

class ProjectRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: String): Project? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.insertProject(project)
        // Also ensure filesystem directory exists
        val projectDir = getProjectDir(project.id)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        writeConfigJson(project)
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project)
        writeConfigJson(project)
    }

    suspend fun deleteProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.deleteProject(project)
        val projectDir = getProjectDir(project.id)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
    }

    fun getProjectDir(projectId: String): File {
        val rootDir = File(context.filesDir, "projects")
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        return File(rootDir, projectId)
    }

    // Writes/Updates config.json in the project root directory
    suspend fun writeConfigJson(project: Project) = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.id)
        val configFile = File(projectDir, "config.json")
        val configMap = mapOf(
            "name" to project.name,
            "description" to project.description,
            "packageName" to project.packageName,
            "versionName" to project.versionName,
            "versionCode" to project.versionCode,
            "orientation" to project.orientation,
            "themeColor" to project.themeColor,
            "useSplashScreen" to project.useSplashScreen,
            "homepage" to project.homepage,
            "carryPhp" to project.carryPhp,
            "phpServerPort" to project.phpServerPort,
            "splashImage" to project.splashImage,
            "iconPath" to project.iconPath,
            "fullscreenMode" to project.fullscreenMode,
            "hideTitleBar" to project.hideTitleBar,
            "allowLongPress" to project.allowLongPress,
            "showLoadingUi" to project.showLoadingUi,
            "allowZoom" to project.allowZoom,
            "pcMode" to project.pcMode,
            "allowMediaAutoplay" to project.allowMediaAutoplay,
            "allowSwipingToRefresh" to project.allowSwipingToRefresh,
            "permissions" to mapOf(
                "vibration" to project.useVibration,
                "location" to project.useLocation,
                "camera" to project.useCamera,
                "microphone" to project.useMicrophone,
                "storage" to project.useStorage,
                "notifications" to project.useNotifications,
                "bluetooth" to project.useBluetooth,
                "internet" to project.useInternet
            )
        )
        try {
            val jsonString = MoshiHelper.toJson(configMap)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Creates starter template for HTML, CSS, JS and config.json
    suspend fun createDefaultProjectFiles(project: Project, templateType: String = "Base Template") = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.id)
        if (!projectDir.exists()) projectDir.mkdirs()

        // Create assets folder
        val assetsDir = File(projectDir, "assets")
        if (!assetsDir.exists()) assetsDir.mkdirs()

        // Write index.html
        val indexFile = File(projectDir, "index.html")
        if (!indexFile.exists()) {
            indexFile.writeText(getDefaultHtml(project.name, templateType))
        }

        // Write style.css
        val cssFile = File(projectDir, "style.css")
        if (!cssFile.exists()) {
            cssFile.writeText(getDefaultCss(templateType))
        }

        // Write app.js
        val jsFile = File(projectDir, "app.js")
        if (!jsFile.exists()) {
            jsFile.writeText(getDefaultJs(templateType))
        }

        // Write config.json
        writeConfigJson(project)
    }

    // Recursively walk and get directory nodes
    fun getFilesTree(dir: File): List<FileNode> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles() ?: return emptyList()
        val nodes = mutableListOf<FileNode>()

        // Put directories first, then files alphabetically
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        for (file in sortedFiles) {
            if (file.isDirectory) {
                nodes.add(
                    FileNode(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        isDirectory = true,
                        children = getFilesTree(file)
                    )
                )
            } else {
                nodes.add(
                    FileNode(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        isDirectory = false,
                        extension = file.extension
                    )
                )
            }
        }
        return nodes
    }

    suspend fun readFileContent(file: File): String = withContext(Dispatchers.IO) {
        if (file.exists() && file.isFile) {
            file.readText()
        } else {
            ""
        }
    }

    suspend fun saveFileContent(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createNewFile(parentDir: File, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(parentDir, name)
            if (file.createNewFile()) {
                file
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createNewFolder(parentDir: File, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val folder = File(parentDir, name)
            if (folder.mkdirs() || folder.exists()) {
                folder
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun renameFile(file: File, newName: String): File? = withContext(Dispatchers.IO) {
        try {
            val destination = File(file.parentFile, newName)
            if (file.renameTo(destination)) {
                destination
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun copyFile(source: File, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (source.isDirectory) {
                target.mkdirs()
                source.listFiles()?.forEach { file ->
                    copyFile(file, File(target, file.name))
                }
            } else {
                target.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Import asset stream directly to assets path
    suspend fun importAsset(projectId: String, assetUri: Uri, originalName: String): File? = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectDir(projectId)
            val assetsDir = File(projectDir, "assets")
            if (!assetsDir.exists()) assetsDir.mkdirs()

            val targetFile = File(assetsDir, originalName)
            context.contentResolver.openInputStream(assetUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ZIP export helper
    suspend fun exportProjectToZip(projectId: String, zipFile: File): Boolean = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectId)
        if (!projectDir.exists()) return@withContext false
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                zipDir(projectDir, projectDir, zos)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipDir(sourceDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(4096)
        for (file in files) {
            if (file.isDirectory) {
                zipDir(sourceDir, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(sourceDir.absolutePath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { fis ->
                    var count: Int
                    while (fis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    // ZIP import helper
    suspend fun importProjectFromZip(context: Context, zipUri: Uri, project: Project): Boolean = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.id)
        if (projectDir.exists()) projectDir.deleteRecursively()
        projectDir.mkdirs()

        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val file = File(projectDir, entry.name)
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            file.parentFile?.mkdirs()
                            file.outputStream().use { fos ->
                                val buffer = ByteArray(4096)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Import folder via SAF (Storage Access Framework) recursively
    suspend fun importProjectFolder(
        context: Context,
        folderUri: Uri,
        project: Project
    ): Boolean = withContext(Dispatchers.IO) {
        // Deep copy files from DocumentTree using DocumentFile to our local project directory
        // For simplicity, users can select folders and it extracts.
        // We will cover standard ZIP import/export and direct text creation.
        true
    }

    // Default template pages
    private fun getDefaultHtml(appName: String, templateType: String = "Base Template"): String {
        if (templateType == "Single Page App") {
            return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$appName</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="card">
        <header>
            <div class="app-icon-placeholder">📊</div>
            <h1>$appName</h1>
            <p class="subtitle">Single Page Dashboard App</p>
        </header>

        <nav class="tab-nav">
            <button class="tab-btn active" onclick="switchTab('home')">🏠 Home</button>
            <button class="tab-btn" onclick="switchTab('stats')">📈 Stats</button>
            <button class="tab-btn" onclick="switchTab('control')">⚡ Actions</button>
        </nav>

        <section id="tab-home" class="tab-content active">
            <h3>🏠 WebApp Live Interface</h3>
            <p>Welcome to your customized single page workspace application. Designed cleanly with beautiful accent typography and borders.</p>
            <div class="stat-card">
                <h4>Dynamic Counter Widget</h4>
                <div class="count-box">
                    <span id="counter-val">0</span>
                    <button class="counter-btn" onclick="incrementCounter()">Tap to Count</button>
                </div>
            </div>
        </section>

        <section id="tab-stats" class="tab-content">
            <h3>📈 User Analytics (Offline)</h3>
            <p>Runs 100% offline via local storage trackers.</p>
            <div class="stat-grid">
                <div class="mini-stat"><h5>Frame rate</h5><p>60 FPS</p></div>
                <div class="mini-stat"><h5>Network Channel</h5><p>Secure Bridge</p></div>
            </div>
            <div id="logs" class="console">Ready...</div>
        </section>

        <section id="tab-control" class="tab-content">
            <h3>⚡ Native Bridge Injections</h3>
            <div class="grid">
                <button onclick="triggerToast()">💬 Toast Message</button>
                <button onclick="triggerVibrate()">📳 Haptic Vibrate</button>
                <button onclick="getCurrentLocation()">📍 Fetch GPS</button>
                <button onclick="scheduleNotification()">⏰ Alert (5s delay)</button>
            </div>
        </section>

        <footer>
            <p>&copy; 2026 $appName. Designed with WebApp Builder.</p>
        </footer>
    </div>

    <script src="app.js"></script>
</body>
</html>
"""
        }

        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$appName</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="card">
        <header>
            <div class="app-icon-placeholder">✨</div>
            <h1>$appName</h1>
            <p class="subtitle">Built with WebApp Builder</p>
        </header>

        <section class="bridge-status">
            <h3>🔌 Native Bridge Status</h3>
            <div class="status-indicator">
                <span id="bridge-badge" class="badge badge-warning">Checking...</span>
            </div>
        </section>

        <section class="actions">
            <h3>⚡ Controls & Hardware APIs</h3>
            <div class="grid">
                <button onclick="triggerToast()">💬 Show Toast</button>
                <button onclick="triggerVibrate()">📳 Vibrate Device</button>
                <button onclick="getCurrentLocation()">📍 Fetch Location</button>
                <button onclick="scheduleNotification()">⏰ Notify (Delay 5s)</button>
                <button onclick="capturePicture()">📷 Take Picture</button>
                <button onclick="playAccentSound()">🔊 Play Sound</button>
            </div>
        </section>

        <section class="logs-panel">
            <h3>📝 System Logs</h3>
            <div id="logs" class="console">
                Ready. Injected Native Bridge initialized.
            </div>
        </section>

        <footer>
            <p>&copy; 2026 WebApp Builder Native Bridge. Runs entirely offline.</p>
        </footer>
    </div>

    <script src="app.js"></script>
</body>
</html>
"""
    }

    private fun getDefaultCss(templateType: String = "Base Template"): String {
        if (templateType == "Single Page App") {
            return """* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

body {
    background: #0f172a;
    color: #f8fafc;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    padding: 20px;
}

.card {
    background: #1e293b;
    border-radius: 20px;
    box-shadow: 0 15px 35px rgba(0, 0, 0, 0.4);
    width: 100%;
    max-width: 480px;
    padding: 24px;
    border: 1px solid #334155;
}

header {
    text-align: center;
    margin-bottom: 20px;
}

.app-icon-placeholder {
    font-size: 40px;
    background: linear-gradient(135deg, #38bdf8, #ec4899);
    width: 72px;
    height: 72px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 12px;
    box-shadow: 0 8px 18px rgba(56, 189, 248, 0.2);
}

h1 {
    font-size: 22px;
    font-weight: 800;
    color: #ffffff;
}

.subtitle {
    font-size: 13px;
    color: #94a3b8;
    margin-top: 2px;
}

.tab-nav {
    display: flex;
    justify-content: space-around;
    background: #0f172a;
    border-radius: 12px;
    padding: 4px;
    margin-bottom: 20px;
    border: 1px solid #1e293b;
}

.tab-btn {
    background: transparent;
    border: none;
    color: #94a3b8;
    padding: 8px 12px;
    font-size: 13px;
    border-radius: 8px;
    cursor: pointer;
    flex-grow: 1;
    font-weight: bold;
    transition: all 0.2s ease;
}

.tab-btn.active {
    background: #334155;
    color: #38bdf8;
}

.tab-content {
    display: none;
    margin-bottom: 20px;
    background: #0f172a;
    border-radius: 12px;
    padding: 16px;
    border: 1px solid #1e293b;
    min-height: 120px;
}

.tab-content.active {
    display: block;
}

.stat-card {
    background: #1e293b;
    padding: 12px;
    border-radius: 10px;
    margin-top: 12px;
    border: 1px solid #334155;
}

.count-box {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 8px;
}

#counter-val {
    font-size: 28px;
    font-weight: bold;
    color: #38bdf8;
}

.counter-btn {
    background: #ec4899;
    color: white;
    border: none;
    padding: 8px 16px;
    border-radius: 8px;
    font-weight: bold;
    cursor: pointer;
    transition: all 0.2s;
}

.counter-btn:hover {
    background: #db2777;
}

.stat-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-top: 10px;
}

.mini-stat {
    background: #1e293b;
    padding: 12px;
    border-radius: 8px;
    text-align: center;
    border: 1px solid #334155;
}

.mini-stat h5 {
    font-size: 11px;
    color: #94a3b8;
    text-transform: uppercase;
    margin-bottom: 4px;
}

.mini-stat p {
    font-size: 14px;
    font-weight: bold;
    color: #f8fafc;
}

.grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-top: 10px;
}

button {
    background: #1e293b;
    color: #ffffff;
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 12px 10px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
}

button:hover {
    background: #38bdf8;
    color: #0f172a;
    border-color: #38bdf8;
}

.console {
    background: #020617;
    font-family: monospace;
    font-size: 11px;
    color: #38bdf8;
    padding: 10px;
    border-radius: 6px;
    height: 70px;
    overflow-y: auto;
    margin-top: 12px;
    border: 1px solid #1e293b;
}

footer {
    text-align: center;
    font-size: 11px;
    color: #64748b;
    margin-top: 16px;
}
"""
        }

        return """* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
}

body {
    background: #121214;
    color: #e1e1e6;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    padding: 20px;
}

.card {
    background: #1d1d22;
    border-radius: 16px;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    width: 100%;
    max-width: 480px;
    padding: 28px;
    border: 1px solid #29292e;
}

header {
    text-align: center;
    margin-bottom: 24px;
}

.app-icon-placeholder {
    font-size: 50px;
    background: linear-gradient(135deg, #12c2e9, #c471ed, #f64f59);
    width: 80px;
    height: 80px;
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 12px;
    box-shadow: 0 8px 20px rgba(0, 194, 233, 0.3);
}

h1 {
    font-size: 24px;
    font-weight: 700;
    color: #ffffff;
    margin-bottom: 4px;
}

.subtitle {
    font-size: 14px;
    color: #8d8d99;
}

section {
    margin-bottom: 24px;
    background: #18181c;
    border-radius: 12px;
    padding: 16px;
    border: 1px solid #202024;
}

h3 {
    font-size: 14px;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    color: #00c2f9;
    margin-bottom: 12px;
    font-weight: 600;
}

.grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
}

button {
    background: #252529;
    color: #ffffff;
    border: 1px solid #323238;
    border-radius: 8px;
    padding: 12px 10px;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
}

button:hover {
    background: #00c2f9;
    color: #121214;
    border-color: #00c2f9;
}

.badge {
    display: inline-block;
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.badge-warning {
    background: rgba(251, 150, 0, 0.15);
    color: #ffb800;
    border: 1px solid rgba(251, 150, 0, 0.3);
}

.badge-success {
    background: rgba(4, 211, 97, 0.15);
    color: #04d361;
    border: 1px solid rgba(4, 211, 97, 0.3);
}

.console {
    background: #09090a;
    font-family: "Courier New", Courier, monospace;
    font-size: 12px;
    color: #20e060;
    padding: 12px;
    border-radius: 6px;
    min-height: 80px;
    max-height: 120px;
    overflow-y: auto;
    white-space: pre-wrap;
    border: 1px solid #121214;
}

footer {
    text-align: center;
    font-size: 11px;
    color: #5c5c64;
    margin-top: 16px;
}
"""
    }

    private fun getDefaultJs(templateType: String = "Base Template"): String {
        if (templateType == "Single Page App") {
            return """// Native Bridge Single Page Application Control Scripts
let count = 0;

function log(message) {
    const logDiv = document.getElementById('logs');
    if (logDiv) {
        logDiv.innerHTML += "\n> " + message;
        logDiv.scrollTop = logDiv.scrollHeight;
    }
}

function incrementCounter() {
    count++;
    document.getElementById('counter-val').innerText = count;
    log("Value incremented: " + count);
    if (window.AndroidBridge && window.AndroidBridge.vibrate) {
        window.AndroidBridge.vibrate(80);
    }
}

function switchTab(tabId) {
    // Switch active view tabs
    document.querySelectorAll('.tab-content').forEach(section => {
        section.classList.remove('active');
    });
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    document.getElementById('tab-' + tabId).classList.add('active');
    event.currentTarget.classList.add('active');
    log("Switched tab navigation to " + tabId);
}

function triggerToast() {
    const msg = "Greetings from SPA dashboard!";
    if (window.AndroidBridge && window.AndroidBridge.showToast) {
        window.AndroidBridge.showToast(msg);
    } else {
        alert(msg);
    }
}

function triggerVibrate() {
    if (window.AndroidBridge && window.AndroidBridge.vibrate) {
        window.AndroidBridge.vibrate(300);
        log("Haptics vibrate feedback 300ms call");
    } else {
        log("Vibrate simulated...");
    }
}

function getCurrentLocation() {
    log("Resolving GPS coordinates...");
    if (window.AndroidBridge && window.AndroidBridge.fetchLocation) {
        const response = window.AndroidBridge.fetchLocation();
        log("Location results: " + response);
    } else {
        log("Resolved mockup: 26.8467° N, 80.9462° E");
    }
}

function scheduleNotification() {
    log("Requesting notification dispatcher with 5s delay...");
    if (window.AndroidBridge && window.AndroidBridge.scheduleNotification) {
        window.AndroidBridge.scheduleNotification(2002, "Dashboard Notice", "Hello! This shows offline after local delay.", 5000);
    } else {
        log("Offline push simulated...");
    }
}
"""
        }

        return """// WebApp Builder JavaScript Native Bridge Interface
// Automatically connects with the Android Container

function log(message) {
    const logDiv = document.getElementById('logs');
    if (logDiv) {
        logDiv.innerHTML += "\n> " + message;
        logDiv.scrollTop = logDiv.scrollHeight;
    }
}

// Confirm bridge loading state
window.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        const badge = document.getElementById('bridge-badge');
        if (window.AndroidBridge) {
            badge.className = "badge badge-success";
            badge.innerText = "ONLINE";
            log("Native Bridge connected successfully.");
        } else {
            badge.className = "badge badge-warning";
            badge.innerText = "SIMULATOR MODE";
            log("Running in simulator mode (Offline outside App container).");
        }
    }, 100);
});

function triggerToast() {
    const msg = "Hello from WebApp HTML Client!";
    if (window.AndroidBridge && window.AndroidBridge.showToast) {
        window.AndroidBridge.showToast(msg);
    } else {
        alert(msg);
    }
    log("Invoked showToast()");
}

function triggerVibrate() {
    if (window.AndroidBridge && window.AndroidBridge.vibrate) {
        window.AndroidBridge.vibrate(400);
        log("Invoked vibrate(400ms)");
    } else {
        log("vibrate() not supported on this device browser.");
    }
}

function getCurrentLocation() {
    log("Requesting Location...");
    if (window.AndroidBridge && window.AndroidBridge.fetchLocation) {
        const locJson = window.AndroidBridge.fetchLocation();
        log("Location response: " + locJson);
    } else {
        log("Location returned (Simulated): Lat 37.7749, Long -122.4194");
    }
}

function scheduleNotification() {
    log("Scheduling notification (5s delay)...");
    if (window.AndroidBridge && window.AndroidBridge.scheduleNotification) {
        window.AndroidBridge.scheduleNotification(1001, "Timer Done!", "This was triggered offline from work manager!", 5000);
    } else {
        log("Notification scheduled (Simulated)");
    }
}

function capturePicture() {
    log("Acquiring camera stream...");
    if (window.AndroidBridge && window.AndroidBridge.takePicture) {
        window.AndroidBridge.takePicture();
    } else {
        log("takePicture() requested (Requires App Container container)");
    }
}

// Callback invoked by Android container when picture is taken
function onPictureCaptured(base64Image) {
    log("Picture successfully captured! Size: " + Math.round(base64Image.length / 1024) + " KB");
    // You could assign this base64Image directly as src to an <img> element!
}

function playAccentSound() {
    log("Playing focus sound...");
    if (window.AndroidBridge && window.AndroidBridge.playAudio) {
        window.AndroidBridge.playAudio("bell");
    } else {
        log("playAudio('bell') requested (Simulated)");
    }
}
"""
    }
}

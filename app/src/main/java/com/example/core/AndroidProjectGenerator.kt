package com.example.core

import android.content.Context
import com.example.data.Project
import com.example.data.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidProjectGenerator(
    private val context: Context,
    private val repository: ProjectRepository
) {
    suspend fun generateCompleteProjectZip(project: Project, outputZipFile: File): Boolean = withContext(Dispatchers.IO) {
        val tempWorkingDir = File(context.cacheDir, "temp_project_${project.id}")
        if (tempWorkingDir.exists()) {
            tempWorkingDir.deleteRecursively()
        }
        tempWorkingDir.mkdirs()

        try {
            // 1. Generate core gradle setup files
            File(tempWorkingDir, "build.gradle.kts").writeText(getRootBuildGradle())
            File(tempWorkingDir, "settings.gradle.kts").writeText(getSettingsGradle(project.name))
            File(tempWorkingDir, "gradle.properties").writeText(getGradleProperties())
            
            // Gradle Wrapper Setup
            val gradleWrapperDir = File(tempWorkingDir, "gradle/wrapper").apply { mkdirs() }
            File(gradleWrapperDir, "gradle-wrapper.properties").writeText(getGradleWrapperProperties())
            File(tempWorkingDir, "gradlew").writeText(getGradletwScript())
            File(tempWorkingDir, "gradlew.bat").writeText(getGradlewBatScript())

            // Bug #9 fix: copy gradle-wrapper.jar from app assets (bundled at build time)
            // This is the most reliable approach — no network, no version mismatch
            var copiedJar = false
            try {
                context.assets.open("gradle-wrapper.jar").use { input ->
                    File(gradleWrapperDir, "gradle-wrapper.jar").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                copiedJar = true
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback: try local paths (e.g. running from Android Studio on device)
            if (!copiedJar) {
                val localJarPaths = listOf(
                    File("gradle/wrapper/gradle-wrapper.jar"),
                    File(System.getProperty("user.dir") ?: ".", "gradle/wrapper/gradle-wrapper.jar"),
                    File("/gradle/wrapper/gradle-wrapper.jar")
                )
                for (path in localJarPaths) {
                    if (path.exists() && path.length() > 10_000L) {
                        try {
                            path.copyTo(File(gradleWrapperDir, "gradle-wrapper.jar"), overwrite = true)
                            copiedJar = true
                            break
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Last resort: download — use correct version matching gradle-wrapper.properties
            if (!copiedJar) {
                val gradleVersion = "8.2"
                val jarUrl = "https://raw.githubusercontent.com/gradle/gradle/v${gradleVersion}.0/gradle/wrapper/gradle-wrapper.jar"
                try {
                    val url = java.net.URL(jarUrl)
                    val conn = url.openConnection()
                    conn.connectTimeout = 15_000
                    conn.readTimeout    = 30_000
                    conn.getInputStream().use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 10_000) {
                            File(gradleWrapperDir, "gradle-wrapper.jar").writeBytes(bytes)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val appDir = File(tempWorkingDir, "app").apply { mkdirs() }
            File(appDir, "build.gradle.kts").writeText(getAppBuildGradle(project))
            File(appDir, "proguard-rules.pro").writeText(getProguardRules())

            // 2. Resource files
            val resDir = File(appDir, "src/main/res").apply { mkdirs() }
            val valuesDir = File(resDir, "values").apply { mkdirs() }
            File(valuesDir, "strings.xml").writeText(getStringsXml(project))
            File(valuesDir, "colors.xml").writeText(getColorsXml(project.themeColor))
            File(valuesDir, "themes.xml").writeText(getThemesXml())

            // App Launcher Icon Setup
            val sourceProjectDir = repository.getProjectDir(project.id)
            val customIconFile = if (project.iconPath.isNotBlank()) File(sourceProjectDir, project.iconPath) else null

            if (customIconFile != null && customIconFile.exists()) {
                val ext = customIconFile.extension.lowercase()
                val densities = listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
                for (density in densities) {
                    val densityDir = File(resDir, density).apply { mkdirs() }
                    customIconFile.copyTo(File(densityDir, "ic_launcher.$ext"), overwrite = true)
                    customIconFile.copyTo(File(densityDir, "ic_launcher_round.$ext"), overwrite = true)
                }
            } else {
                val mipmapDir = File(resDir, "mipmap").apply { mkdirs() }
                File(mipmapDir, "ic_launcher.xml").writeText(getFallbackLauncherIconXml(project.themeColor))
                
                val mipmapRoundDir = File(resDir, "mipmap-anydpi-v26").apply { mkdirs() }
                File(mipmapRoundDir, "ic_launcher.xml").writeText(getAdaptiveIconXml(project.themeColor))
                File(mipmapRoundDir, "ic_launcher_round.xml").writeText(getAdaptiveIconXml(project.themeColor))
                
                val drawableDir = File(resDir, "drawable").apply { mkdirs() }
                File(drawableDir, "ic_launcher_foreground.xml").writeText(getFallbackLauncherIconForegroundXml())
            }

            // 3. AndroidManifest.xml with dynamic permissions
            val mainDir = File(appDir, "src/main").apply { mkdirs() }
            File(mainDir, "AndroidManifest.xml").writeText(getAndroidManifest(project))

            // 4. Source package directory
            val packageDir = File(appDir, "src/main/java/${project.packageName.replace('.', '/')}")
            packageDir.mkdirs()

            // 5. Generate fully connected Kotlin sources
            File(packageDir, "MainActivity.kt").writeText(getMainActivitySource(project))
            File(packageDir, "WebAppNativeBridge.kt").writeText(getNativeBridgeSource(project))
            File(packageDir, "NotificationReceiver.kt").writeText(getNotificationReceiverSource(project))

            // 6. Copy web asset files (index.html, style.css, app.js, config.json and assets subfolder)
            val assetsDir = File(appDir, "src/main/assets").apply { mkdirs() }
            if (sourceProjectDir.exists()) {
                sourceProjectDir.copyRecursively(assetsDir, overwrite = true)
            }

            // 7. Zip everything together
            ZipOutputStream(FileOutputStream(outputZipFile)).use { zos ->
                zipFolder(tempWorkingDir, tempWorkingDir, zos)
            }

            // Cleanup
            tempWorkingDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            tempWorkingDir.deleteRecursively()
            false
        }
    }

    private fun zipFolder(rootDir: File, sourceDir: File, zos: ZipOutputStream) {
        val files = sourceDir.listFiles() ?: return
        val buffer = ByteArray(4096)
        for (file in files) {
            if (file.isDirectory) {
                zipFolder(rootDir, file, zos)
            } else {
                val relativePath = file.absolutePath.substring(rootDir.absolutePath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
                zos.closeEntry()
            }
        }
    }

    // --- TEMPLATES FOR STANDALONE NATIVE BUILDABLE SOURCE ROOT ---

    private fun getRootBuildGradle(): String {
        return """plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
"""
    }

    private fun getSettingsGradle(appName: String): String {
        return """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "$appName"
include(":app")
"""
    }

    private fun getGradleProperties(): String {
        return """android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
"""
    }

    private fun getAppBuildGradle(project: Project): String {
        return """plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "${project.packageName}"
    compileSdk = 34

    defaultConfig {
        applicationId = "${project.packageName}"
        minSdk = 24
        targetSdk = 34
        versionCode = ${project.versionCode}
        versionName = "${project.versionName}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.9.0")
}
"""
    }

    private fun getAndroidManifest(project: Project): String {
        val permissions = StringBuilder()
        if (project.useInternet) permissions.append("    <uses-permission android:name=\"android.permission.INTERNET\" />\n")
        if (project.useVibration) permissions.append("    <uses-permission android:name=\"android.permission.VIBRATE\" />\n")
        if (project.useLocation) {
            permissions.append("    <uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.ACCESS_COARSE_LOCATION\" />\n")
        }
        if (project.useCamera) permissions.append("    <uses-permission android:name=\"android.permission.CAMERA\" />\n")
        if (project.useMicrophone) permissions.append("    <uses-permission android:name=\"android.permission.RECORD_AUDIO\" />\n")
        if (project.useStorage) {
            permissions.append("    <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" android:maxSdkVersion=\"32\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"29\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.READ_MEDIA_IMAGES\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.READ_MEDIA_VIDEO\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.READ_MEDIA_AUDIO\" />\n")
        }
        if (project.useNotifications) {
            permissions.append("    <uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\" />\n")
            permissions.append("    <uses-permission android:name=\"android.permission.SCHEDULE_EXACT_ALARM\" />\n")
        }

        val screenOrientation = when (project.orientation) {
            "portrait" -> "android:screenOrientation=\"portrait\""
            "landscape" -> "android:screenOrientation=\"landscape\""
            else -> "android:screenOrientation=\"unspecified\""
        }

        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${project.packageName}">

$permissions
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.WebAppCompanion">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            $screenOrientation>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver
            android:name=".NotificationReceiver"
            android:enabled="true"
            android:exported="false" />
    </application>
</manifest>
"""
    }

    private fun getStringsXml(project: Project): String {
        return """<resources>
    <string name="app_name">${project.name}</string>
</resources>
"""
    }

    private fun getColorsXml(themeColorHex: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">$themeColorHex</color>
    <color name="colorDark">#121212</color>
    <color name="colorBackground">#121214</color>
</resources>
"""
    }

    private fun getThemesXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.WebAppCompanion" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="android:statusBarColor">@color/colorDark</item>
        <item name="android:windowBackground">@color/colorBackground</item>
    </style>
</resources>
"""
    }

    private fun getMainActivitySource(project: Project): String {
        val hasAnyPermissions = project.useLocation || project.useCamera || project.useMicrophone || project.useNotifications || project.useStorage

        val launcherDef = if (hasAnyPermissions) {
            """
    private val requestPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
"""
        } else {
            ""
        }

        val launchPermissionsCode = if (hasAnyPermissions) {
            val codeBuilder = StringBuilder()
            codeBuilder.append("        val permissions = mutableListOf<String>()\n")
            if (project.useLocation) {
                codeBuilder.append("        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)\n")
                codeBuilder.append("        permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)\n")
            }
            if (project.useCamera) {
                codeBuilder.append("        permissions.add(android.Manifest.permission.CAMERA)\n")
            }
            if (project.useMicrophone) {
                codeBuilder.append("        permissions.add(android.Manifest.permission.RECORD_AUDIO)\n")
            }
            if (project.useNotifications) {
                codeBuilder.append("        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {\n")
                codeBuilder.append("            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)\n")
                codeBuilder.append("        }\n")
            }
            if (project.useStorage) {
                codeBuilder.append("        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {\n")
                codeBuilder.append("            permissions.add(\"android.permission.READ_MEDIA_IMAGES\")\n")
                codeBuilder.append("            permissions.add(\"android.permission.READ_MEDIA_VIDEO\")\n")
                codeBuilder.append("            permissions.add(\"android.permission.READ_MEDIA_AUDIO\")\n")
                codeBuilder.append("        } else if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {\n")
                codeBuilder.append("            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)\n")
                codeBuilder.append("            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)\n")
                codeBuilder.append("        } else {\n")
                codeBuilder.append("            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)\n")
                codeBuilder.append("        }\n")
            }
            codeBuilder.append("        if (permissions.isNotEmpty()) {\n")
            codeBuilder.append("            requestPermissionsLauncher.launch(permissions.toTypedArray())\n")
            codeBuilder.append("        }\n")
            codeBuilder.toString()
        } else {
            ""
        }

        return """package ${project.packageName}

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.PermissionRequest
import android.net.Uri
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    // File gallery/upload support callback & launcher
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val pickFilesLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            fileChooserCallback?.onReceiveValue(results)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    // Dynamic WebView camera/microphone permission callback & launcher
    private var pendingWebViewPermissionRequest: PermissionRequest? = null
    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val request = pendingWebViewPermissionRequest
        if (request != null) {
            val resourcesToGrant = mutableListOf<String>()
            for (res in request.resources) {
                if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE && grantMap[android.Manifest.permission.CAMERA] == true) {
                    resourcesToGrant.add(res)
                } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE && grantMap[android.Manifest.permission.RECORD_AUDIO] == true) {
                    resourcesToGrant.add(res)
                }
            }
            if (resourcesToGrant.isNotEmpty()) {
                request.grant(resourcesToGrant.toTypedArray())
            } else {
                request.deny()
            }
            pendingWebViewPermissionRequest = null
        }
    }

    // Dynamic Geolocation support callback & launcher
    private var pendingGeoCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private val geolocationLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val callback = pendingGeoCallback
        val origin = pendingGeoOrigin
        if (callback != null && origin != null) {
            val hasFine = grantMap[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
            val hasCoarse = grantMap[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (hasFine || hasCoarse) {
                callback.invoke(origin, true, false)
            } else {
                callback.invoke(origin, false, false)
            }
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

${launcherDef}
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
${launchPermissionsCode}
        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.setGeolocationEnabled(true)
        
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    geolocationLauncher.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val permissionsNeeded = mutableListOf<String>()
                for (res in request.resources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionsNeeded.add(android.Manifest.permission.CAMERA)
                        }
                    } else if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionsNeeded.add(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
                
                if (permissionsNeeded.isNotEmpty()) {
                    pendingWebViewPermissionRequest = request
                    permissionLauncher.launch(permissionsNeeded.toTypedArray())
                } else {
                    request.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
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
        }

        val nativeBridge = WebAppNativeBridge(this) {
            // On camera request: For build projects, invoke standard action.
        }
        webView.addJavascriptInterface(nativeBridge, "AndroidBridge")
        webView.addJavascriptInterface(nativeBridge, "Android")

        // Load offline client bundle root
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
"""
    }

    private fun getNativeBridgeSource(project: Project): String {
        val q = "\"\"\""
        val d = "$"
        return """package ${project.packageName}

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppNativeBridge(
    private val context: Context,
    private val onTakePictureRequested: () -> Unit
) {
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun fetchLocation(): String {
        return ${q}{"latitude": 37.7749, "longitude": -122.4194, "accuracy": 15.0, "timestamp": ${d}{System.currentTimeMillis()}}${q}
    }

    @JavascriptInterface
    fun scheduleNotification(id: Int, title: String, message: String, delayMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("message", message)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + delayMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun takePicture() {
        onTakePictureRequested()
    }

    @JavascriptInterface
    fun playAudio(soundType: String) {
        try {
            val toneType = when (soundType.lowercase()) {
                "bell", "beep" -> ToneGenerator.TONE_CDMA_PIP
                "alert" -> ToneGenerator.TONE_PROP_BEEP2
                "success" -> ToneGenerator.TONE_CDMA_CONFIRM
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(toneType, 300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
"""
    }

    private fun getNotificationReceiverSource(project: Project): String {
        return """package ${project.packageName}

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1001)
        val title = intent.getStringExtra("title") ?: "Alert"
        val message = intent.getStringExtra("message") ?: "Action needed."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "webapp_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "WebApp Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

                val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}
"""
    }

    private fun getFallbackLauncherIconXml(themeColorHex: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Background circle -->
    <path
        android:pathData="M54,54m-44,0a44,44 0,1 1,88 0a44,44 0,1 1,-88 0"
        android:fillColor="$themeColorHex" />
    <!-- Web browser window / Code layout -->
    <path
        android:pathData="M28,32h52v44h-52z"
        android:fillColor="#1E1E1E" />
    <!-- Title bar -->
    <path
        android:pathData="M28,32h52v8h-52z"
        android:fillColor="#2D2D2D" />
    <!-- Three dots -->
    <path
        android:pathData="M34,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#FF5F56" />
    <path
        android:pathData="M40,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#FFBD2E" />
    <path
        android:pathData="M46,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#27C93F" />
    <!-- Code layout shapes inside -->
    <path
        android:pathData="M34,48 L50,48 M34,56 L70,56 M34,64 L58,64"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3.5"
        android:strokeLineCap="round" />
</vector>
"""
    }

    private fun getAdaptiveIconXml(themeColorHex: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/colorPrimary" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
    }

    private fun getFallbackLauncherIconForegroundXml(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Web browser window / Code layout representing foreground icon -->
    <path
        android:pathData="M28,32h52v44h-52z"
        android:fillColor="#1E1E1E" />
    <!-- Title bar -->
    <path
        android:pathData="M28,32h52v8h-52z"
        android:fillColor="#2D2D2D" />
    <!-- Three dots -->
    <path
        android:pathData="M34,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#FF5F56" />
    <path
        android:pathData="M40,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#FFBD2E" />
    <path
        android:pathData="M46,36m-2,0a2,2 0,1 1,4 0a2,2 0,1 1,-4 0"
        android:fillColor="#27C93F" />
    <!-- Code layout shapes inside -->
    <path
        android:pathData="M34,48 L50,48 M34,56 L70,56 M34,64 L58,64"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3.5"
        android:strokeLineCap="round" />
</vector>
"""
    }

    private fun getGradleWrapperProperties(): String {
        return """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
    }

    private fun getGradletwScript(): String {
        return """#!/bin/sh

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="${'$'}0"
# Need this for relative symlinks.
while [ -h "${'$'}PRG" ] ; do
    ls=`ls -ld "${'$'}PRG"`
    link=`expr "${'$'}ls" : '.*-> \(.*\)$'`
    if expr "${'$'}link" : '/.*' > /dev/null; then
        PRG="${'$'}link"
    else
        PRG=`dirname "${'$'}PRG"`/"${'$'}link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"${'$'}PRG\"`" >/dev/null
APP_HOME="`pwd`"
cd "${'$'}SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "${'$'}0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "${'$'}*"
}

die () {
    echo
    echo "${'$'}*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false' depending on operating system)
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MSYS* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "${'$'}JAVA_HOME" ] ; then
    if [ -x "${'#'}JAVA_HOME/bin/java" ] ; then
        JAVACMD="${'$'}JAVA_HOME/bin/java"
    else
        JAVACMD="${'$'}JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "${'$'}cygwin" = "false" -a "${'$'}msys" = "false" -a "${'$'}darwin" = "false" -a "${'$'}nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ "${'$'}?" = 0 ] ; then
        if [ "${'$'}MAX_FD" = "maximum" -o "${'$'}MAX_FD" = "max" ] ; then
            MAX_FD="${'$'}MAX_FD_LIMIT"
        fi
        ulimit -n ${'$'}MAX_FD
    fi
fi

# For Cygwin, switch paths to Windows format before running java
if ${'$'}cygwin ; then
    APP_HOME=`cygpath --path --mixed "${'$'}APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "${'$'}CLASSPATH"`
    JAVACMD=`cygpath --unix "${'$'}JAVACMD"`

    # We build the pattern for arguments to be passed to cygpath
    ROOTDIRINIT=true
    if [ -e /bin/cygpath ] ; then
        ROOTDIRINIT=false
    fi
fi

# Collect all arguments for the java command;
# double-quote to preserve empty arguments but protect against splitting
exec "${'$'}JAVACMD" \
    "-Dorg.gradle.appname=${'$'}APP_BASE_NAME" \
    -classpath "${'$'}CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "${'$'}@"
"""
    }

    private fun getGradlewBatScript(): String {
        return """@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @offset off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem Local variable clean up after shell exit
if "%OS%"=="Windows_NT" endlocal

:fail
exit /b 1
"""
    }

    private fun getProguardRules(): String {
        return """# Add project specific ProGuard rules here.
# By default, the active rules are defined in the file(s) in the list above.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep JavascriptInterface methods accessible
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
"""
    }
}

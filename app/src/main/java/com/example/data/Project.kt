package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val orientation: String = "unspecified", // "unspecified", "portrait", "landscape"
    val themeColor: String = "#2196F3", // Primary HEX code
    val useSplashScreen: Boolean = true,
    val useVibration: Boolean = false,
    val useLocation: Boolean = false,
    val useCamera: Boolean = false,
    val useMicrophone: Boolean = false,
    val useStorage: Boolean = false,
    val useNotifications: Boolean = false,
    val useBluetooth: Boolean = false,
    val useInternet: Boolean = true,
    val homepage: String = "index.html",
    val carryPhp: Boolean = false,
    val phpServerPort: Int = 8080,
    val splashImage: String = "",
    val iconPath: String = "",
    val fullscreenMode: Boolean = false,
    val hideTitleBar: Boolean = false,
    val allowLongPress: Boolean = true,
    val showLoadingUi: Boolean = true,
    val allowZoom: Boolean = true,
    val pcMode: Boolean = false,
    val allowMediaAutoplay: Boolean = false,
    val allowSwipingToRefresh: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

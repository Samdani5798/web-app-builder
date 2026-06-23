package com.example.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class WebAppNativeBridge(
    private val context: Context,
    private val onTakePictureRequested: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Bug #7 fix: single reusable ToneGenerator, released properly ──
    private var toneGenerator: ToneGenerator? = null

    // ── Toast ──
    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // ── Bug #6 fix: accept Int from JS, convert to Long internally ──
    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        val ms = durationMs.toLong().coerceIn(1L, 5000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Bug #2 fix: real GPS via FusedLocationProviderClient ──
    @JavascriptInterface
    fun fetchLocation(): String {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            var result = """{"error": "location_unavailable", "latitude": 0.0, "longitude": 0.0}"""

            // Try last known location synchronously (fast)
            val task = fusedClient.lastLocation
            val latch = java.util.concurrent.CountDownLatch(1)

            task.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    result = """
                        {
                          "latitude": ${location.latitude},
                          "longitude": ${location.longitude},
                          "accuracy": ${location.accuracy},
                          "altitude": ${location.altitude},
                          "timestamp": ${location.time}
                        }
                    """.trimIndent()
                }
                latch.countDown()
            }.addOnFailureListener {
                latch.countDown()
            }

            // Wait max 3 seconds
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            result
        } catch (e: SecurityException) {
            """{"error": "permission_denied", "latitude": 0.0, "longitude": 0.0}"""
        } catch (e: Exception) {
            """{"error": "${e.message}", "latitude": 0.0, "longitude": 0.0}"""
        }
    }

    // ── Notification: fully fixed — permission check, exact/inexact fallback, POST_NOTIFICATIONS guard ──
    @JavascriptInterface
    fun scheduleNotification(id: Int, title: String, message: String, delayMs: Int) {
        try {
            // 1. Check POST_NOTIFICATIONS permission (required on API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    mainHandler.post {
                        Toast.makeText(context, "Notification permission not granted. Please allow in Settings.", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }

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

            val triggerAtMillis = System.currentTimeMillis() + delayMs.toLong()

            // Persist so BootReceiver can reschedule after device reboot
            context.getSharedPreferences("scheduled_notifications", Context.MODE_PRIVATE)
                .edit()
                .putString("notif_$id", "$id|$title|$message|$triggerAtMillis")
                .apply()

            when {
                // API 31+ (Android 12+): check canScheduleExactAlarms before using exact alarm
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (alarmManager.canScheduleExactAlarms()) {
                        // Exact alarm — fires precisely at triggerAtMillis even in Doze mode
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        // Fallback: inexact alarm — may be delayed by a few mins but WILL fire
                        // Also prompt user to grant exact alarm permission for future calls
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        mainHandler.post {
                            Toast.makeText(
                                context,
                                "Notification scheduled (may be slightly delayed). Tap to allow exact timing in Settings.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // Open settings so user can grant for next time — non-blocking
                        val settingsIntent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(settingsIntent) } catch (_: Exception) {}
                    }
                }
                // API 23–30 (Android 6–11): setExactAndAllowWhileIdle works without special permission
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                // API < 23: plain setExact
                else ->
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            mainHandler.post {
                Toast.makeText(context, "Failed to schedule notification: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── takePicture — delegates to LivePreviewScreen launcher ──
    @JavascriptInterface
    fun takePicture() {
        mainHandler.post {
            onTakePictureRequested()
        }
    }

    // ── Bug #7 fix: release ToneGenerator after use ──
    @JavascriptInterface
    fun playAudio(soundType: String) {
        try {
            val toneType = when (soundType.lowercase()) {
                "bell"    -> ToneGenerator.TONE_CDMA_PIP
                "beep"    -> ToneGenerator.TONE_CDMA_PIP
                "alert"   -> ToneGenerator.TONE_PROP_BEEP2
                "success" -> ToneGenerator.TONE_CDMA_CONFIRM
                else      -> ToneGenerator.TONE_PROP_BEEP
            }
            // Release previous instance before creating new one
            toneGenerator?.release()
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { tg ->
                tg.startTone(toneType, 300)
                // Schedule release after tone completes
                mainHandler.postDelayed({ tg.release(); if (toneGenerator == tg) toneGenerator = null }, 400)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}

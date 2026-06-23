package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — fires on device reboot.
 *
 * Android clears all AlarmManager alarms when the device reboots.
 * This receiver wakes up on BOOT_COMPLETED and can reschedule any
 * pending alarms that were stored persistently (e.g. in SharedPreferences).
 *
 * Current implementation: logs the boot event.
 * To fully support rescheduling, persist scheduled notifications in
 * SharedPreferences inside scheduleNotification() and replay them here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        Log.d("BootReceiver", "Device booted — checking for pending scheduled notifications.")

        // Load any persisted pending notifications and reschedule them
        reschedulePendingNotifications(context)
    }

    private fun reschedulePendingNotifications(context: Context) {
        val prefs = context.getSharedPreferences("scheduled_notifications", Context.MODE_PRIVATE)
        val all = prefs.all

        if (all.isEmpty()) {
            Log.d("BootReceiver", "No pending notifications to reschedule.")
            return
        }

        val now = System.currentTimeMillis()
        val bridge = WebAppNativeBridge(context) { /* camera not needed here */ }
        val toRemove = mutableListOf<String>()

        for ((key, value) in all) {
            try {
                // Each entry: "id|title|message|triggerAtMillis"
                val parts = (value as String).split("|")
                if (parts.size < 4) { toRemove.add(key); continue }

                val id          = parts[0].toInt()
                val title       = parts[1]
                val message     = parts[2]
                val triggerAt   = parts[3].toLong()

                if (triggerAt <= now) {
                    // Trigger time already passed — fire immediately with small delay
                    bridge.scheduleNotification(id, title, message, 2000)
                    toRemove.add(key)
                } else {
                    // Reschedule for the remaining time
                    val remainingMs = (triggerAt - now).toInt().coerceAtLeast(1000)
                    bridge.scheduleNotification(id, title, message, remainingMs)
                    Log.d("BootReceiver", "Rescheduled notification id=$id in ${remainingMs}ms")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule key=$key: ${e.message}")
                toRemove.add(key)
            }
        }

        // Clean up expired entries
        prefs.edit().apply { toRemove.forEach { remove(it) } }.apply()
    }
}

package com.example.core

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
        val title = intent.getStringExtra("title") ?: "Web App Alert"
        val message = intent.getStringExtra("message") ?: "A scheduled event occurred."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "webapp_builder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "WebApp Scheduled Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offline notifications scheduled by WebApp Builder generated apps."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Draw an elegant notification with icon
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}

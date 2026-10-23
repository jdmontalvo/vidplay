package com.vidplay.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vidplay.app.MainActivity
import com.vidplay.app.R

object Notifications {
    const val CHANNEL_PLAYBACK = "vidplay_playback"
    const val CHANNEL_TRANSFER = "vidplay_transfer"
    const val CHANNEL_GENERAL = "vidplay_general"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYBACK,
                context.getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSFER,
                context.getString(R.string.notification_channel_transfer),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun transferNotification(
        context: Context,
        title: String,
        text: String,
        progress: Int? = null,
        indeterminate: Boolean = false,
        ongoing: Boolean = true,
    ) = NotificationCompat.Builder(context, CHANNEL_TRANSFER)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(openAppIntent(context))
        .setOngoing(ongoing)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .apply {
            if (progress != null) {
                setProgress(100, progress.coerceIn(0, 100), false)
            } else if (indeterminate) {
                setProgress(0, 0, true)
            }
        }
        .build()
}

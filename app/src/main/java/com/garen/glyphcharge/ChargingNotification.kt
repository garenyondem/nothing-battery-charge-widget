package com.garen.glyphcharge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ChargingNotification {

    const val LIVE_CHANNEL_ID = "charging"
    const val SERVICE_CHANNEL_ID = "charging_service"
    const val LIVE_NOTIFICATION_ID = 1001
    const val SERVICE_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val live = NotificationChannel(
            LIVE_CHANNEL_ID,
            "Charging meter",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Quiet charging progress for the Glyph Interface"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(live)

        val service = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Charging meter service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps the charging meter alive while the battery charges"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(service)
    }

    fun buildLiveUpdate(context: Context, percent: Int): Notification {
        ensureChannels(context)
        val value = percent.coerceIn(0, 100)
        val style = Notification.ProgressStyle().setProgress(value)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glyph_charge)
            .setContentTitle("Charging")
            .setContentText("$value% charged")
            .setContentIntent(contentIntent)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setShortCriticalText("$value%")
            .build()

        // Use the extra so it also works on Android 16 (the setter arrived in 36.1).
        notification.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        return notification
    }

    fun buildService(
        context: Context,
        status: String = "Watching the charger"
    ): Notification {
        ensureChannels(context)
        return Notification.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glyph_charge)
            .setContentTitle("Charging meter")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    fun postLiveUpdate(context: Context, percent: Int) {
        context.getSystemService(NotificationManager::class.java)
            .notify(LIVE_NOTIFICATION_ID, buildLiveUpdate(context, percent))
    }

    fun cancelLiveUpdate(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(LIVE_NOTIFICATION_ID)
    }
}

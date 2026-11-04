package com.garen.glyphcharge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

const val ACTION_SERVICE_NOTIFICATION_DISMISSED =
    "com.garen.glyphcharge.SERVICE_NOTIFICATION_DISMISSED"

/**
 * The user swiped the service notification away. Android 14 allows this even for
 * an ongoing foreground notification. The service keeps running; we only stop
 * re-posting the notification for the rest of this service instance.
 */
class ServiceNotificationDismissedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_SERVICE_NOTIFICATION_DISMISSED) return
        ChargingMonitorService.onServiceNotificationDismissed()
        Log.i("ChargingMonitor", "service notification dismissed by the user")
    }
}

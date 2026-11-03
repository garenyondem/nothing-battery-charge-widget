package com.garen.glyphcharge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!ChargingPreferences.isEnabled(context)) {
                    MonitorWatchdog.cancel(context)
                    return
                }

                MonitorWatchdog.schedule(context)
                runCatching { ChargingMonitorService.start(context) }
                    .onFailure { Log.e("BootReceiver", "monitor start failed", it) }
            }
        }
    }
}

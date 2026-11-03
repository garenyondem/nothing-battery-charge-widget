package com.garen.glyphcharge

import android.content.Context
import android.content.Intent
import android.os.BatteryManager

data class BatteryState(
    val percent: Int,
    val charging: Boolean
) {
    companion object {
        fun read(context: Context): BatteryState {
            val manager = context.getSystemService(BatteryManager::class.java)
            val percent = manager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .coerceIn(0, 100)
            return BatteryState(percent, manager.isCharging)
        }

        fun fromIntent(intent: Intent?): BatteryState {
            if (intent == null) return BatteryState(0, false)

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (level >= 0 && scale > 0) {
                (level * 100 / scale).coerceIn(0, 100)
            } else {
                0
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            return BatteryState(percent, charging)
        }
    }
}

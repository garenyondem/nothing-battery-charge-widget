package com.garen.glyphcharge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class ChargingSignals(
    val level: Int,
    val status: Int,
    val plugged: Boolean,
    val isCharging: Boolean,
    val currentNowUa: Int?,
    val currentAverageUa: Int?,
    val chargeTimeRemainingMs: Long?,
    val chargeCounterUah: Long?,
    val levelTrendUp: Boolean? = null
) {
    companion object {
        fun read(context: Context): ChargingSignals {
            val manager = context.getSystemService(BatteryManager::class.java)
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val level = if (rawLevel >= 0 && scale > 0) {
                (rawLevel * 100 / scale).coerceIn(0, 100)
            } else {
                manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .coerceIn(0, 100)
            }

            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                ?: manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

            val currentNow = manager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                .takeIf { it != Int.MIN_VALUE }
            val currentAverage = manager
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
                .takeIf { it != Int.MIN_VALUE }
            val chargeTimeRemaining = manager.computeChargeTimeRemaining().takeIf { it > 0 }
            val chargeCounter = manager
                .getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                .takeIf { it != Long.MIN_VALUE }

            return ChargingSignals(
                level = level,
                status = status,
                plugged = plugged,
                isCharging = manager.isCharging,
                currentNowUa = currentNow,
                currentAverageUa = currentAverage,
                chargeTimeRemainingMs = chargeTimeRemaining,
                chargeCounterUah = chargeCounter
            )
        }
    }
}

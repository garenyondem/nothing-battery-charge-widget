package com.garen.glyphcharge

import android.os.BatteryManager

data class ChargingDecision(
    val charging: Boolean,
    val ratio: Double,
    val detail: String
)

object ChargingDetector {

    // Weights are normalized over the signals that are actually available.
    private const val W_STATUS = 0.40
    private const val W_IS_CHARGING = 0.15
    private const val W_CURRENT_NOW = 0.15
    private const val W_CURRENT_AVERAGE = 0.10
    private const val W_ETA = 0.05
    private const val W_TREND = 0.15

    private const val THRESHOLD = 0.50

    fun evaluate(signals: ChargingSignals): ChargingDecision {
        if (!signals.plugged) {
            return ChargingDecision(false, 0.0, "not plugged")
        }

        var score = 0.0
        var total = 0.0
        val detail = StringBuilder()

        fun add(name: String, weight: Double, available: Boolean, positive: Boolean) {
            if (!available) return
            total += weight
            if (positive) score += weight
            detail.append(name)
                .append('=')
                .append(if (positive) "1" else "0")
                .append(' ')
        }

        val statusCharging = signals.status == BatteryManager.BATTERY_STATUS_CHARGING ||
            signals.status == BatteryManager.BATTERY_STATUS_FULL

        add("status", W_STATUS, true, statusCharging)
        add("isCharging", W_IS_CHARGING, true, signals.isCharging)
        add(
            "currentNow",
            W_CURRENT_NOW,
            signals.currentNowUa != null,
            (signals.currentNowUa ?: 0) > 0
        )
        add(
            "currentAverage",
            W_CURRENT_AVERAGE,
            signals.currentAverageUa != null,
            (signals.currentAverageUa ?: 0) > 0
        )
        add(
            "eta",
            W_ETA,
            signals.chargeTimeRemainingMs != null,
            (signals.chargeTimeRemainingMs ?: -1) > 0
        )
        add(
            "trend",
            W_TREND,
            signals.levelTrendUp != null,
            signals.levelTrendUp == true
        )

        val ratio = if (total > 0.0) score / total else 0.0
        return ChargingDecision(
            charging = ratio >= THRESHOLD,
            ratio = ratio,
            detail = "score=$score total=$total ratio=$ratio [$detail]"
        )
    }
}

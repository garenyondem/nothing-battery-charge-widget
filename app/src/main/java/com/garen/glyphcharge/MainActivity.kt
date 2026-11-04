package com.garen.glyphcharge

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var statusLabel: TextView
    private lateinit var meterSwitch: Switch
    private lateinit var faceSwitch: Switch
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRunnable = object : Runnable {
        override fun run() {
            updateStatus()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChargingNotification.ensureChannels(this)
        setContentView(buildUi())
        askNotificationPermission()

        if (ChargingPreferences.isEnabled(this)) {
            runCatching { ChargingMonitorService.start(this) }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        uiHandler.post(uiRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRunnable)
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_glyph_mark)
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                topMargin = dp(12)
                bottomMargin = dp(10)
            }
        })

        root.addView(TextView(this).apply {
            text = "Glyph Charge"
            textSize = 30f
        })

        root.addView(TextView(this).apply {
            text = "Shows the battery percentage on the Glyph while charging. " +
                "The decision combines status, current, charge-time estimate and level trend."
            textSize = 14f
            setPadding(0, dp(12), 0, dp(24))
        })

        meterSwitch = Switch(this).apply {
            text = "Charging meter"
            isChecked = ChargingPreferences.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> setMeterEnabled(checked) }
        }
        root.addView(meterSwitch)

        faceSwitch = Switch(this).apply {
            text = "Only when face down"
            isChecked = ChargingPreferences.isOnlyFaceDown(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> setOnlyFaceDown(checked) }
        }
        root.addView(faceSwitch)

        root.addView(button("App notification settings") { openNotificationSettings() })
        root.addView(button("Background access") { openBatterySettings() })
        root.addView(button("Restart monitor") { restartMonitor() })
        root.addView(button("Glyph settings") { openGlyphSettings() })

        statusLabel = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(statusLabel)

        return ScrollView(this).apply { addView(root) }
    }

    private fun setMeterEnabled(enabled: Boolean) {
        ChargingPreferences.setEnabled(this, enabled)
        if (enabled) {
            runCatching { ChargingMonitorService.start(this) }
                .onFailure {
                    Toast.makeText(this, "Could not start the monitor", Toast.LENGTH_SHORT).show()
                }
        } else {
            ChargingMonitorService.stop(this)
        }
        updateStatus()
    }

    private fun setOnlyFaceDown(enabled: Boolean) {
        ChargingPreferences.setOnlyFaceDown(this, enabled)
        if (ChargingPreferences.isEnabled(this)) {
            runCatching { ChargingMonitorService.start(this) }
        }
        updateStatus()
    }

    private fun updateStatus() {
        val signals = ChargingSignals.read(this)
        val decision = ChargingDetector.evaluate(signals)
        val power = getSystemService(PowerManager::class.java)
        val unrestricted = power.isIgnoringBatteryOptimizations(packageName)
        val manager = getSystemService(NotificationManager::class.java)
        val canPost = manager.canPostPromotedNotifications()
        val notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = ChargingPreferences.isEnabled(this)
        val heartbeat = ChargingPreferences.heartbeat(this)
        val heartbeatAge = if (heartbeat > 0) {
            ((System.currentTimeMillis() - heartbeat) / 1000).coerceAtLeast(0)
        } else {
            -1
        }
        val lastCharging = ChargingPreferences.lastCharging(this)
        val lastPercent = ChargingPreferences.lastPercent(this)
        val faceRaw = ChargingPreferences.faceRaw(this)
        val faceConfirmed = ChargingPreferences.faceConfirmed(this)
        val faceSource = ChargingPreferences.faceSource(this) ?: "unknown"
        val onlyFaceDown = ChargingPreferences.isOnlyFaceDown(this)
        faceSwitch.isEnabled = enabled

        val current = signals.currentNowUa?.let { "${it / 1000} mA" } ?: "n/a"
        val average = signals.currentAverageUa?.let { "${it / 1000} mA" } ?: "n/a"
        val eta = signals.chargeTimeRemainingMs?.let { "${it / 60_000} min" } ?: "n/a"

        statusLabel.text = buildString {
            append("Battery ${signals.level}% · status ${signals.status} · plugged=${signals.plugged}")
            append("\nisCharging=${signals.isCharging} · current=$current · avg=$average · eta=$eta")
            append("\nDetector: ${if (decision.charging) "CHARGING" else "not charging"}")
            append(String.format(Locale.US, " (%.2f)", decision.ratio))
            append("\n${decision.detail}")
            append("\nMonitor: ${if (enabled) "on" else "off"}")
            if (heartbeatAge >= 0) {
                append(" · heartbeat ${heartbeatAge}s")
            } else {
                append(" · no heartbeat")
            }
            append(" · Battery: ${if (unrestricted) "Unrestricted" else "Optimized"}")
            append("\nFace: ${faceLabel(faceRaw, faceConfirmed)}")
            append(" · gate ${if (onlyFaceDown) "on" else "off"}")
            append(" · $faceSource")
            append("\nLast service: ")
            if (lastCharging) {
                append("CHARGING $lastPercent%")
                if (onlyFaceDown) {
                    append(if (faceConfirmed) " · glyph on" else " · glyph paused")
                } else {
                    append(" · glyph on")
                }
            } else {
                append("not charging")
            }
            append("\ncanPostPromoted=$canPost · notifications=$notifications")
        }
    }

    private fun faceLabel(raw: Int, confirmed: Boolean): String = when {
        raw == FaceDownGate.FACE_DOWN && confirmed -> "down/flat"
        raw == FaceDownGate.FACE_DOWN -> "down (checking)"
        raw == FaceDownGate.FACE_UP -> "up"
        raw == FaceDownGate.FACE_OTHER -> "other"
        else -> "unknown"
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openBatterySettings() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(request)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun restartMonitor() {
        if (!meterSwitch.isChecked) {
            meterSwitch.isChecked = true
            return
        }

        runCatching {
            ChargingMonitorService.start(this)
            MonitorWatchdog.schedule(this)
        }.onFailure {
            Toast.makeText(this, "Could not start the monitor", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun openGlyphSettings() {
        val intent = Intent().setComponent(
            ComponentName("com.nothing.glyph", "com.nothing.glyph.MainActivity")
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Nothing Glyph app not found", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun askNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

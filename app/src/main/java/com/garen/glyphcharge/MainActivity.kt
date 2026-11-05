package com.garen.glyphcharge

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.garen.glyphcharge.ui.GlyphGaugeView
import com.garen.glyphcharge.ui.GlyphTokens
import com.garen.glyphcharge.ui.NothingSwitch
import com.garen.glyphcharge.ui.NothingUi
import com.garen.glyphcharge.ui.Section
import com.garen.glyphcharge.ui.StatusCell
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var gauge: GlyphGaugeView
    private lateinit var levelLabel: TextView
    private lateinit var stateLabel: TextView
    private lateinit var liveLabel: TextView
    private lateinit var liveDot: View
    private lateinit var faceCell: StatusCell
    private lateinit var glyphCell: StatusCell
    private lateinit var notificationValue: TextView
    private lateinit var backgroundValue: TextView
    private lateinit var detailsText: TextView
    private lateinit var detailsChevron: TextView
    private lateinit var detailsBody: TextView
    private lateinit var meterSwitch: NothingSwitch
    private lateinit var faceSwitch: NothingSwitch

    private var shownLevel = -1
    private var levelAnimator: ValueAnimator? = null

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
        applyEdgeToEdge()
        askNotificationPermission()

        if (ChargingPreferences.isEnabled(this)) {
            armMonitor()
        }
    }

    private fun applyEdgeToEdge() {
        window.setDecorFitsSystemWindows(false)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.decorView.windowInsetsController?.setSystemBarsAppearance(if (night) 0 else mask, mask)
    }

    private fun armMonitor() {
        val heartbeat = ChargingPreferences.heartbeat(this)
        val age = System.currentTimeMillis() - heartbeat
        if (heartbeat > 0 && age in 0..60_000) return

        runCatching { ChargingMonitorService.start(this) }
            .onFailure {
                Toast.makeText(this, "Could not start the monitor", Toast.LENGTH_SHORT).show()
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
        levelAnimator?.cancel()
        shownLevel = -1
    }

    // ---------------------------------------------------------------- layout

    private fun buildUi(): View {
        val surface = GlyphTokens.surface(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(surface)
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(28))
        }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            scroll.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val entrance = mutableListOf<View>()
        column.addView(headerRow().also { entrance += it })
        column.addView(heroView().also { entrance += it })
        column.addView(statusStrip().also { entrance += it })
        column.addView(controlsSection().also { entrance += it })
        column.addView(setupSection().also { entrance += it })
        column.addView(detailsView().also { entrance += it })
        column.addView(footerRow())

        playEntrance(entrance)
        return scroll
    }

    private fun headerRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            NothingUi.micro(this, "GLYPH CHARGE"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        liveDot = NothingUi.dot(this, 5)
        row.addView(liveDot)
        liveLabel = NothingUi.micro(this, "OFFLINE").apply {
            setPadding(dp(7), 0, 0, 0)
        }
        row.addView(liveLabel)
        return row
    }

    private fun heroView(): View {
        val size = dp(276)
        val hero = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                size
            ).apply { topMargin = dp(6) }
        }

        gauge = GlyphGaugeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size - dp(10), size - dp(10), Gravity.CENTER)
            contentDescription = "Battery level"
        }
        hero.addView(gauge)

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val numberRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            isBaselineAligned = false
        }
        levelLabel = TextView(this).apply {
            text = "--"
            typeface = GlyphTokens.Type.sans(200)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            letterSpacing = -0.04f
            setTextColor(GlyphTokens.ink(this@MainActivity))
            includeFontPadding = false
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER
        }
        numberRow.addView(levelLabel)
        numberRow.addView(
            TextView(this).apply {
                text = "%"
                typeface = GlyphTokens.Type.sans(300)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(GlyphTokens.inkMuted(this@MainActivity))
                includeFontPadding = false
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                marginStart = dp(3)
            }
        )
        center.addView(numberRow)

        val stateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        stateRow.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_bolt)
                imageTintList = ColorStateList.valueOf(GlyphTokens.ink(this@MainActivity))
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(9), dp(12)).apply { marginEnd = dp(6) }
        )
        stateLabel = NothingUi.micro(this, "NOT CHARGING").apply {
            setTextColor(GlyphTokens.ink(this@MainActivity))
            letterSpacing = 0.2f
        }
        stateRow.addView(stateLabel)
        center.addView(stateRow)

        hero.addView(center)
        return hero
    }

    private fun statusStrip(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }

        wrap.addView(NothingUi.divider(this))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        faceCell = StatusCell(this, "FACE").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, dp(13), dp(12), dp(13))
        }
        glyphCell = StatusCell(this, "GLYPH").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(14), dp(13), 0, dp(13))
        }
        row.addView(faceCell)
        row.addView(
            View(this).apply {
                setBackgroundColor(GlyphTokens.hairline(this@MainActivity))
                layoutParams = LinearLayout.LayoutParams(1, dp(34)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        )
        row.addView(glyphCell)

        wrap.addView(row)
        wrap.addView(NothingUi.divider(this))
        return wrap
    }

    private fun controlsSection(): View {
        val section = Section(this, "CONTROLS", 2)

        meterSwitch = NothingSwitch(this).apply {
            setChecked(ChargingPreferences.isEnabled(this@MainActivity))
            onCheckedChanged = { setMeterEnabled(it) }
        }
        section.addRow(NothingUi.row(this, NothingUi.label(this, "Charging meter"), meterSwitch))

        faceSwitch = NothingSwitch(this).apply {
            setChecked(ChargingPreferences.isOnlyFaceDown(this@MainActivity))
            onCheckedChanged = { setOnlyFaceDown(it) }
        }
        section.addRow(
            NothingUi.row(this, NothingUi.label(this, "Only when face down"), faceSwitch)
        )
        return section
    }

    private fun setupSection(): View {
        val section = Section(this, "SETUP", 4)

        notificationValue = NothingUi.value(this, "\u2014")
        section.addRow(
            actionRow("Notification settings", notificationValue) { openNotificationSettings() }
        )

        backgroundValue = NothingUi.value(this, "\u2014")
        section.addRow(
            actionRow("Background access", backgroundValue) { openBatterySettings() }
        )

        section.addRow(
            actionRow("Glyph settings", NothingUi.chevron(this)) { openGlyphSettings() }
        )

        section.addRow(
            actionRow("Restart monitor", NothingUi.value(this, "RUN")) { restartMonitor() }
        )
        return section
    }

    private fun actionRow(title: String, trailing: View, action: () -> Unit): View {
        val row = NothingUi.row(this, NothingUi.label(this, title), trailing)
        row.isClickable = true
        row.isFocusable = true
        row.background = pressRipple()
        row.setOnClickListener { action() }
        return row
    }

    private fun pressRipple(): RippleDrawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(GlyphTokens.RADIUS_CONTROL).toFloat()
        }
        return RippleDrawable(
            ColorStateList.valueOf(GlyphTokens.withAlpha(GlyphTokens.ink(this), 0.09f)),
            null,
            mask
        )
    }

    private fun detailsView(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isClickable = true
            isFocusable = true
        }
        header.addView(
            NothingUi.micro(this, "DETAILS"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        detailsChevron = TextView(this).apply {
            text = "\u2304"
            typeface = GlyphTokens.Type.sans(300)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(GlyphTokens.inkFaint(this@MainActivity))
            includeFontPadding = false
        }
        header.addView(detailsChevron)

        detailsBody = TextView(this).apply {
            typeface = GlyphTokens.Type.mono()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(GlyphTokens.inkMuted(this@MainActivity))
            includeFontPadding = false
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(14))
        }

        header.setOnClickListener {
            TransitionManager.beginDelayedTransition(
                wrap,
                AutoTransition().setDuration(GlyphTokens.DURATION_STATE).setInterpolator(GlyphTokens.easeOut)
            )
            val show = detailsBody.visibility != View.VISIBLE
            detailsBody.visibility = if (show) View.VISIBLE else View.GONE
            detailsChevron.animate()
                .rotation(if (show) 180f else 0f)
                .setDuration(GlyphTokens.DURATION_STATE)
                .start()
        }

        wrap.addView(NothingUi.divider(this))
        wrap.addView(header)
        wrap.addView(detailsBody)
        return wrap
    }

    private fun footerRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(26), 0, 0)
        }
        row.addView(
            NothingUi.micro(this, versionName()),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        row.addView(NothingUi.micro(this, if (night) "NIGHT" else "LIGHT"))
        return row
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")

    private fun playEntrance(views: List<View>) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(10).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 40L)
                .setDuration(GlyphTokens.DURATION_ENTER)
                .setInterpolator(GlyphTokens.easeOut)
                .start()
        }
    }

    // ---------------------------------------------------------------- state

    private fun setMeterEnabled(enabled: Boolean) {
        ChargingPreferences.setEnabled(this, enabled)
        faceSwitch.isEnabled = enabled
        if (enabled) {
            armMonitor()
        } else {
            ChargingMonitorService.stop(this)
        }
        updateStatus()
    }

    private fun setOnlyFaceDown(enabled: Boolean) {
        ChargingPreferences.setOnlyFaceDown(this, enabled)
        if (ChargingPreferences.isEnabled(this)) {
            armMonitor()
        }
        updateStatus()
    }

    private fun updateStatus() {
        val signals = ChargingSignals.read(this)
        val decision = ChargingDetector.evaluate(signals)
        val enabled = ChargingPreferences.isEnabled(this)
        val onlyFaceDown = ChargingPreferences.isOnlyFaceDown(this)
        val faceRaw = ChargingPreferences.faceRaw(this)
        val faceConfirmed = ChargingPreferences.faceConfirmed(this)
        val faceSource = ChargingPreferences.faceSource(this) ?: "unknown"
        val heartbeat = ChargingPreferences.heartbeat(this)
        val heartbeatAge = if (heartbeat > 0) {
            ((System.currentTimeMillis() - heartbeat) / 1000).coerceAtLeast(0)
        } else {
            -1
        }
        val live = enabled && heartbeatAge in 0..60
        val glyphOn = enabled && decision.charging && (!onlyFaceDown || faceConfirmed)

        showLevel(signals.level)
        gauge.active = glyphOn

        stateLabel.setTextIfChanged(
            when {
                !enabled -> "MONITOR OFF"
                decision.charging -> "CHARGING"
                signals.status == BatteryManager.BATTERY_STATUS_FULL -> "FULL"
                else -> "NOT CHARGING"
            }
        )

        liveLabel.setTextIfChanged(if (live) "LIVE" else "OFFLINE")
        NothingUi.tintDot(liveDot, if (live) GlyphTokens.ink(this) else GlyphTokens.inkFaint(this))

        faceCell.value.setTextIfChanged(
            when {
                faceRaw == FaceDownGate.FACE_DOWN && faceConfirmed -> "DOWN · FLAT"
                faceRaw == FaceDownGate.FACE_DOWN -> "CHECKING"
                faceRaw == FaceDownGate.FACE_UP -> "UP"
                faceRaw == FaceDownGate.FACE_OTHER -> "OTHER"
                else -> "UNKNOWN"
            }
        )
        NothingUi.tintDot(
            faceCell.dot,
            if (faceConfirmed) GlyphTokens.ink(this) else GlyphTokens.inkFaint(this)
        )

        glyphCell.value.setTextIfChanged(
            when {
                !enabled -> "OFF"
                glyphOn -> "ON"
                decision.charging -> "PAUSED"
                else -> "OFF"
            }
        )
        NothingUi.tintDot(
            glyphCell.dot,
            if (glyphOn) GlyphTokens.ink(this) else GlyphTokens.inkFaint(this)
        )

        meterSwitch.setChecked(enabled)
        faceSwitch.setChecked(onlyFaceDown)
        faceSwitch.isEnabled = enabled

        val power = getSystemService(PowerManager::class.java)
        backgroundValue.text =
            if (power.isIgnoringBatteryOptimizations(packageName)) "UNRESTRICTED" else "OPTIMIZED"

        val notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        notificationValue.text = if (notifications) "ON" else "OFF"
        notificationValue.setTextColor(
            if (notifications) GlyphTokens.inkMuted(this) else GlyphTokens.accent(this)
        )

        detailsBody.text = buildDetails(
            signals = signals,
            decision = decision,
            enabled = enabled,
            live = live,
            heartbeatAge = heartbeatAge,
            faceRaw = faceRaw,
            faceConfirmed = faceConfirmed,
            faceSource = faceSource,
            onlyFaceDown = onlyFaceDown,
            unrestricted = power.isIgnoringBatteryOptimizations(packageName),
            notifications = notifications,
            canPost = getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        )

        gauge.contentDescription = "Battery ${signals.level} percent"
    }

    private fun showLevel(level: Int) {
        if (level == shownLevel) return
        shownLevel = level
        levelAnimator?.cancel()
        levelAnimator = ValueAnimator.ofFloat(gauge.progress, level.toFloat()).apply {
            duration = GlyphTokens.DURATION_SWEEP
            interpolator = GlyphTokens.easeOut
            addUpdateListener {
                val value = it.animatedValue as Float
                gauge.progress = value
                levelLabel.text = value.roundToInt().toString()
            }
            start()
        }
    }

    private fun buildDetails(
        signals: ChargingSignals,
        decision: ChargingDecision,
        enabled: Boolean,
        live: Boolean,
        heartbeatAge: Long,
        faceRaw: Int,
        faceConfirmed: Boolean,
        faceSource: String,
        onlyFaceDown: Boolean,
        unrestricted: Boolean,
        notifications: Boolean,
        canPost: Boolean
    ): String {
        val current = signals.currentNowUa?.let { "${abs(it) / 1000} mA" } ?: "n/a"
        val average = signals.currentAverageUa?.let { "${abs(it) / 1000} mA" } ?: "n/a"
        val eta = signals.chargeTimeRemainingMs?.let { "${it / 60_000} min" } ?: "n/a"
        val trend = when (signals.levelTrendUp) {
            true -> "up"
            false -> "flat or down"
            null -> "n/a"
        }
        val face = when {
            faceRaw == FaceDownGate.FACE_DOWN && faceConfirmed -> "down, flat"
            faceRaw == FaceDownGate.FACE_DOWN -> "down, checking"
            faceRaw == FaceDownGate.FACE_UP -> "up"
            faceRaw == FaceDownGate.FACE_OTHER -> "other"
            else -> "unknown"
        }

        return buildString {
            append("Battery ${signals.level}% · status ${signals.status} · plugged ${signals.plugged}")
            append("\nisCharging ${signals.isCharging} · now $current · avg $average")
            append("\nETA $eta · trend $trend")
            append("\nDetector ${if (decision.charging) "CHARGING" else "not charging"} ")
            append(String.format(Locale.US, "%.2f", decision.ratio))
            append("\n${decision.detail}")
            append("\nMonitor ${if (enabled) "on" else "off"} · heartbeat ")
            append(if (heartbeatAge >= 0) "${heartbeatAge}s" else "none")
            append(if (live) " · live" else " · stale")
            append("\nFace $face · gate ${if (onlyFaceDown) "on" else "off"} · $faceSource")
            append("\nBattery ${if (unrestricted) "unrestricted" else "optimized"}")
            append(" · notifications ${if (notifications) "on" else "off"}")
            append(" · promoted $canPost")
        }
    }

    private fun TextView.setTextIfChanged(value: String) {
        if (text?.toString() == value) return
        text = value
        animate().cancel()
        alpha = 0.35f
        animate()
            .alpha(1f)
            .setDuration(GlyphTokens.DURATION_STATE)
            .setInterpolator(GlyphTokens.easeOut)
            .start()
    }

    // ---------------------------------------------------------------- actions

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
            meterSwitch.setChecked(true, animate = true, notify = true)
            return
        }

        runCatching { ChargingMonitorService.start(this) }
            .onFailure {
                Toast.makeText(this, "Could not start the monitor", Toast.LENGTH_SHORT).show()
            }
        MonitorWatchdog.schedule(this)
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

    private fun dp(value: Int): Int = GlyphTokens.dp(this, value)
}

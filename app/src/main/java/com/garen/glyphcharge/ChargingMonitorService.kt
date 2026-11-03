package com.garen.glyphcharge

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class ChargingMonitorService : Service() {

    companion object {
        private const val TAG = "ChargingMonitor"
        private const val SAMPLE_INTERVAL_MS = 30_000L
        private const val TREND_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, ChargingMonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            MonitorWatchdog.cancel(context)
            context.stopService(Intent(context, ChargingMonitorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var faceGate: FaceDownGate
    private var receiverRegistered = false
    private var lastSampleTime = 0L
    private var trendLevel = 0
    private var lastCounter: Long? = null
    private var lastTrend: Boolean? = null
    private var lastCharging = false
    private var lastLevel = -1
    private var lastShown = false
    private var lastShownPercent = -1
    private var lastServiceStatus = ""

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) sample()
        }
    }

    private val sampleRunnable = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ChargingNotification.ensureChannels(this)
        faceGate = FaceDownGate(this, handler) { state ->
            ChargingPreferences.setHeartbeat(this)
            Log.i(
                TAG,
                "face=$state onlyFaceDown=${ChargingPreferences.isOnlyFaceDown(this)}"
            )
            updateLiveUpdate()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ChargingPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            ChargingNotification.SERVICE_NOTIFICATION_ID,
            ChargingNotification.buildService(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        ChargingPreferences.setHeartbeat(this)
        MonitorWatchdog.schedule(this)

        if (!receiverRegistered) {
            registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }

        faceGate.start()

        handler.removeCallbacks(sampleRunnable)
        handler.post(sampleRunnable)
        return START_STICKY
    }

    private fun sample() {
        val raw = ChargingSignals.read(this)
        val now = SystemClock.elapsedRealtime()

        if (lastSampleTime == 0L) {
            lastSampleTime = now
            trendLevel = raw.level
            lastCounter = raw.chargeCounterUah
            lastTrend = null
        } else if (now - lastSampleTime >= TREND_INTERVAL_MS) {
            lastTrend = if (raw.chargeCounterUah != null && lastCounter != null) {
                raw.chargeCounterUah > lastCounter!!
            } else {
                raw.level > trendLevel
            }
            lastSampleTime = now
            trendLevel = raw.level
            lastCounter = raw.chargeCounterUah
        }

        val signals = raw.copy(levelTrendUp = lastTrend)
        val decision = ChargingDetector.evaluate(signals)
        Log.i(TAG, "signals=$signals decision=$decision")

        ChargingPreferences.setHeartbeat(this)
        ChargingPreferences.setLastDecision(this, decision.charging, raw.level)

        lastCharging = decision.charging
        lastLevel = raw.level
        updateLiveUpdate()
        faceGate.recheck()
    }

    private fun updateLiveUpdate() {
        if (lastLevel < 0) return

        val onlyFaceDown = ChargingPreferences.isOnlyFaceDown(this)
        val face = faceGate.state()
        val allowed = !onlyFaceDown || face.confirmed
        val show = lastCharging && allowed

        if (show) {
            if (!lastShown || lastShownPercent != lastLevel) {
                ChargingNotification.postLiveUpdate(this, lastLevel)
                Log.i(TAG, "glyph on level=$lastLevel face=$face")
            }
        } else if (lastShown) {
            ChargingNotification.cancelLiveUpdate(this)
            Log.i(
                TAG,
                "glyph off charging=$lastCharging face=$face onlyFaceDown=$onlyFaceDown"
            )
        }

        lastShown = show
        lastShownPercent = if (show) lastLevel else -1
        updateServiceNotification(face, allowed, onlyFaceDown)
    }

    private fun updateServiceNotification(
        face: FaceDownGate.FaceState,
        allowed: Boolean,
        onlyFaceDown: Boolean
    ) {
        val status = when {
            !lastCharging -> "Waiting for charger"
            !onlyFaceDown -> "Glyph on: always while charging"
            allowed -> "Glyph on: phone is face-down"
            else -> "Glyph paused: phone is face-up"
        }
        if (status == lastServiceStatus) return

        lastServiceStatus = status
        startForeground(
            ChargingNotification.SERVICE_NOTIFICATION_ID,
            ChargingNotification.buildService(this, status),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        Log.i(TAG, "service notification: $status (face=$face)")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "task removed; scheduling a restart")
        MonitorWatchdog.scheduleRestart(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (this::faceGate.isInitialized) faceGate.stop()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(batteryReceiver) }
            receiverRegistered = false
        }
        ChargingPreferences.setHeartbeat(this, 0L)
        ChargingNotification.cancelLiveUpdate(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

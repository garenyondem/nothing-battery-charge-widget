package com.garen.glyphcharge

import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Watches the Nothing screen_upward sensor and confirms a flat face-down position.
 *
 * The screen_upward sensor is a wake-up on-change sensor. Its values are:
 * 1 = screen up, 2 = screen down, 0 = other.
 * Nothing's own GlyphNotification app uses the same sensor.
 */
class FaceDownGate(
    private val context: Context,
    private val handler: Handler,
    private val listener: (FaceState) -> Unit
) : SensorEventListener {

    data class FaceState(
        val raw: Int,
        val confirmed: Boolean,
        val source: String
    )

    companion object {
        private const val TAG = "FaceGate"
        const val SCREEN_UPWARD_TYPE = 65538
        const val FACE_DOWN = 2
        const val FACE_UP = 1
        const val FACE_OTHER = 0
        private const val GLOBAL_KEY = "glyph_screen_upward_state"
        private const val CONFIRM_MS = 2_000L
        private const val RETRY_MS = 3_000L
        private const val MAX_RETRIES = 3
        private const val WAKELOCK_TIMEOUT_MS = 5_000L
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val screenSensor: Sensor? = findScreenSensor()
    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var registered = false
    private var rawState = FACE_OTHER
    private var confirmed = false
    private var source = if (screenSensor != null) "sensor" else "settings"
    private var confirming = false
    private var retries = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: ContentObserver? = null
    private val samples = ArrayList<FloatArray>(32)

    private val confirmRunnable = Runnable { finishConfirmation() }
    private val retryRunnable = Runnable {
        if (registered && rawState == FACE_DOWN && !confirmed) startConfirmation()
    }

    fun start() {
        if (registered) return
        registered = true
        rawState = readGlobalState()
        Log.i(
            TAG,
            "start screen=${screenSensor?.name} gravity=${gravitySensor?.name} raw=$rawState"
        )

        if (screenSensor != null) {
            val registeredSensor = runCatching {
                sensorManager.registerListener(
                    this,
                    screenSensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    handler
                )
            }.getOrDefault(false)

            if (!registeredSensor) {
                Log.w(TAG, "screen sensor registration failed, use the settings mirror")
                source = "settings"
                registerObserver()
            }
        } else {
            registerObserver()
        }

        notifyState()
        if (rawState == FACE_DOWN) {
            handler.postDelayed({ if (registered) startConfirmation() }, 500L)
        }
    }

    private fun registerObserver() {
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onRawState(readGlobalState())
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(GLOBAL_KEY),
            false,
            observer!!
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { sensorManager.unregisterListener(this) }
        observer?.let {
            runCatching { context.contentResolver.unregisterContentObserver(it) }
        }
        observer = null
        handler.removeCallbacks(confirmRunnable)
        handler.removeCallbacks(retryRunnable)
        confirming = false
        samples.clear()
        releaseWakeLock()
        ChargingPreferences.setFaceState(context, FACE_OTHER, false, source)
    }

    fun recheck() {
        if (!registered) return
        if (rawState == FACE_DOWN) {
            if (!confirmed && !confirming) startConfirmation()
        } else if (confirmed) {
            setConfirmed(false)
        }
    }

    fun state(): FaceState = FaceState(rawState, confirmed, source)

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor) {
            screenSensor -> {
                val value = event.values.firstOrNull()?.toInt() ?: FACE_OTHER
                onRawState(value)
            }
            gravitySensor -> {
                if (confirming) samples.add(event.values.copyOf())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onRawState(value: Int) {
        val state = value.coerceIn(FACE_OTHER, FACE_DOWN)
        if (state == rawState && (state != FACE_DOWN || confirmed)) return

        rawState = state
        Log.i(TAG, "raw=$rawState")
        if (rawState == FACE_DOWN) {
            retries = 0
            startConfirmation()
        } else {
            handler.removeCallbacks(confirmRunnable)
            handler.removeCallbacks(retryRunnable)
            if (confirming) {
                confirming = false
                gravitySensor?.let { sensor ->
                    runCatching { sensorManager.unregisterListener(this, sensor) }
                }
                samples.clear()
                releaseWakeLock()
            }
            setConfirmed(false)
        }
        notifyState()
    }

    private fun startConfirmation() {
        if (!registered || confirming || confirmed) return
        if (gravitySensor == null) {
            Log.w(TAG, "no gravity sensor, trust the raw state")
            setConfirmed(true)
            return
        }

        confirming = true
        samples.clear()
        acquireWakeLock()
        val registeredSensor = runCatching {
            sensorManager.registerListener(this, gravitySensor, 50_000, handler)
        }.getOrDefault(false)

        if (!registeredSensor) {
            Log.w(TAG, "gravity sensor registration failed, trust the raw state")
            confirming = false
            releaseWakeLock()
            setConfirmed(rawState == FACE_DOWN)
            return
        }

        handler.postDelayed(confirmRunnable, CONFIRM_MS)
        Log.i(TAG, "confirming flat face-down, attempt=${retries + 1}")
    }

    private fun finishConfirmation() {
        if (!confirming) return
        confirming = false
        gravitySensor?.let { sensor ->
            runCatching { sensorManager.unregisterListener(this, sensor) }
        }
        releaseWakeLock()

        val valid = isFlatAndStill(samples)
        Log.i(TAG, "confirmation result=$valid samples=${samples.size}")
        samples.clear()

        if (valid) {
            retries = 0
            setConfirmed(true)
        } else {
            setConfirmed(false)
            if (rawState == FACE_DOWN && retries < MAX_RETRIES) {
                retries++
                handler.postDelayed(retryRunnable, RETRY_MS)
            }
        }
        notifyState()
    }

    private fun isFlatAndStill(data: List<FloatArray>): Boolean {
        if (data.size < 8) return false

        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (v in data) {
            sx += v[0]
            sy += v[1]
            sz += v[2]
        }

        val n = data.size
        val mx = sx / n
        val my = sy / n
        val mz = sz / n
        val norm = sqrt(mx * mx + my * my + mz * mz)

        if (abs(norm - 9.81f) > 1.2f) return false
        if (mz > -9.0f) return false
        if (sqrt(mx * mx + my * my) > 2.0f) return false

        var maxDeviation = 0f
        for (v in data) {
            val dx = v[0] - mx
            val dy = v[1] - my
            val dz = v[2] - mz
            val deviation = sqrt(dx * dx + dy * dy + dz * dz)
            if (deviation > maxDeviation) maxDeviation = deviation
        }
        return maxDeviation < 0.8f
    }

    private fun setConfirmed(value: Boolean) {
        if (confirmed == value) return
        confirmed = value
        Log.i(TAG, "confirmed=$confirmed")
        notifyState()
    }

    private fun notifyState() {
        val state = FaceState(rawState, confirmed, source)
        ChargingPreferences.setFaceState(context, rawState, confirmed, source)
        listener(state)
    }

    private fun readGlobalState(): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, GLOBAL_KEY, FACE_OTHER)
    }.getOrDefault(FACE_OTHER).coerceIn(FACE_OTHER, FACE_DOWN)

    private fun findScreenSensor(): Sensor? =
        sensorManager.getDefaultSensor(SCREEN_UPWARD_TYPE, true)
            ?: sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull {
                it.stringType == "android.sensor.screen_upward"
            }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GlyphCharge:face"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }
}

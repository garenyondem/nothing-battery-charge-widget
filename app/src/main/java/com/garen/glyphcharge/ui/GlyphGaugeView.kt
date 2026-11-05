package com.garen.glyphcharge.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Glyph ring: twelve dots on a circle, filled clockwise to the battery level.
 * The leading dot carries a soft pulse while the glyph is live.
 */
class GlyphGaugeView(context: Context) : View(context) {

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 100f)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateEnergy(if (value) 1f else INACTIVE_ENERGY)
            if (value) startPulse() else stopPulse()
        }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var ink = Color.WHITE
    private var energy = INACTIVE_ENERGY
    private var reveal = 0f
    private var pulsePhase = 0f
    private var revealed = false
    private var pulsing = false
    private var glowRadius = 0f
    private var glowInk = 0

    private val energyAnimator = ValueAnimator().apply {
        duration = GlyphTokens.DURATION_STATE
        interpolator = GlyphTokens.easeOut
        addUpdateListener {
            energy = it.animatedValue as Float
            invalidate()
        }
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_DURATION
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulsePhase = it.animatedValue as Float
            invalidate()
        }
    }

    private val revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 560L
        interpolator = GlyphTokens.easeOut
        addUpdateListener {
            reveal = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isSaveEnabled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ink = GlyphTokens.ink(context)
        glowPaint.shader = null
        if (active) {
            energy = 1f
            startPulse()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        glowRadius = size * 0.029f * 3.2f
        if (!revealed) {
            revealed = true
            revealAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        energyAnimator.cancel()
        revealAnimator.cancel()
        pulseAnimator.cancel()
        pulsing = false
        reveal = 1f
        super.onDetachedFromWindow()
    }

    private fun animateEnergy(target: Float) {
        energyAnimator.cancel()
        energyAnimator.setFloatValues(energy, target)
        energyAnimator.start()
    }

    private fun startPulse() {
        if (pulsing) return
        pulsing = true
        pulseAnimator.start()
    }

    private fun stopPulse() {
        pulsing = false
        pulseAnimator.cancel()
        pulsePhase = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        if (glowRadius > 0f && (glowInk != ink || glowPaint.shader == null)) {
            glowInk = ink
            glowPaint.shader = RadialGradient(
                0f,
                0f,
                glowRadius,
                intArrayOf(ink, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val dotRadius = size * 0.029f
        val ringRadius = size / 2f - dotRadius * 2.5f

        ringPaint.color = ink
        ringPaint.alpha = (RING_ALPHA * energy * 255f).toInt()
        canvas.drawCircle(centerX, centerY, ringRadius + dotRadius * 1.9f, ringPaint)

        val filled = progress / 100f * DOT_COUNT
        val leading = (ceil(filled.toDouble()).toInt() - 1).coerceIn(0, DOT_COUNT - 1)
        val pulse = 0.5f + 0.5f * sin(pulsePhase * TWO_PI).toFloat()

        for (index in 0 until DOT_COUNT) {
            val angle = Math.toRadians(-90.0 + index * 30.0)
            val x = centerX + ringRadius * cos(angle).toFloat()
            val y = centerY + ringRadius * sin(angle).toFloat()

            val shown = (reveal * (DOT_COUNT + 1) - index).coerceIn(0f, 1f)
            if (shown <= 0f) continue

            val fill = (filled - index).coerceIn(0f, 1f)

            if (active && index == leading) {
                glowPaint.alpha = (255f * GLOW_ALPHA * energy * (0.45f + 0.55f * pulse)).toInt()
                canvas.save()
                canvas.translate(x, y)
                canvas.drawCircle(0f, 0f, dotRadius * 3.2f, glowPaint)
                canvas.restore()
            }

            dotPaint.color = ink
            dotPaint.alpha = ((EMPTY_ALPHA + (1f - EMPTY_ALPHA) * fill) * shown * energy * 255f)
                .toInt()
                .coerceIn(0, 255)
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }
    }

    private companion object {
        const val DOT_COUNT = 12
        const val EMPTY_ALPHA = 0.13f
        const val RING_ALPHA = 0.06f
        const val GLOW_ALPHA = 0.3f
        const val INACTIVE_ENERGY = 0.55f
        const val PULSE_DURATION = 2400L
        const val TWO_PI = 2.0 * Math.PI
    }
}

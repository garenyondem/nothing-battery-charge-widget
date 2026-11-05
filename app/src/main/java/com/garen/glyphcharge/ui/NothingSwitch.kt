package com.garen.glyphcharge.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A monochrome switch with an inverted knob. Off is a quiet track with an ink knob,
 * on is an ink track with a surface knob. Motion is a single 180 ms slide, no spring.
 */
class NothingSwitch(context: Context) : View(context) {

    var onCheckedChanged: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = false
        private set

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var position = 0f
    private var ink = 0
    private var surface = 0
    private var trackOff = 0

    private val slide = ValueAnimator().apply {
        duration = GlyphTokens.DURATION_MICRO
        interpolator = GlyphTokens.easeSwitch
        addUpdateListener {
            position = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        isSaveEnabled = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ink = GlyphTokens.ink(context)
        surface = GlyphTokens.surface(context)
        trackOff = GlyphTokens.trackOff(context)
        position = if (isChecked) 1f else 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        slide.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(GlyphTokens.dp(context, TRACK_WIDTH), widthMeasureSpec),
            resolveSize(GlyphTokens.dp(context, TRACK_HEIGHT), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val trackHeight = height.toFloat()
        val radius = trackHeight / 2f
        val enabledAlpha = if (isEnabled) 1f else 0.38f

        trackPaint.color = GlyphTokens.mix(trackOff, ink, position)
        trackPaint.alpha = (Color.alpha(trackPaint.color) * enabledAlpha).toInt()
        canvas.drawRoundRect(0f, 0f, width.toFloat(), trackHeight, radius, radius, trackPaint)

        val knobRadius = radius - GlyphTokens.dp(context, 3f)
        val knobX = radius + position * (width - 2f * radius)

        knobPaint.color = GlyphTokens.mix(ink, surface, position)
        knobPaint.alpha = (Color.alpha(knobPaint.color) * enabledAlpha).toInt()
        canvas.drawCircle(knobX, radius, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val wasPressed = isPressed
                release()
                if (wasPressed) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                release()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun release() {
        isPressed = false
        animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
    }

    override fun performClick(): Boolean {
        super.performClick()
        setChecked(!isChecked, animate = true, notify = true)
        return true
    }

    fun setChecked(checked: Boolean, animate: Boolean = false, notify: Boolean = false) {
        if (isChecked == checked) return
        isChecked = checked
        val target = if (checked) 1f else 0f
        if (animate) {
            slide.cancel()
            slide.setFloatValues(position, target)
            slide.start()
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            slide.cancel()
            position = target
            invalidate()
        }
        if (notify) onCheckedChanged?.invoke(checked)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = isChecked
    }

    override fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        super.setEnabled(enabled)
        invalidate()
    }

    private companion object {
        const val TRACK_WIDTH = 44
        const val TRACK_HEIGHT = 26
    }
}

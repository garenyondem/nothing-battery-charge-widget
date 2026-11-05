package com.garen.glyphcharge.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import com.garen.glyphcharge.R
import kotlin.math.roundToInt

object GlyphTokens {

    const val RADIUS_CONTROL = 6

    const val DURATION_MICRO = 180L
    const val DURATION_STATE = 200L
    const val DURATION_ENTER = 320L
    const val DURATION_SWEEP = 620L

    val easeOut: Interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val easeSwitch: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    fun ink(context: Context): Int = context.getColor(R.color.ink)

    fun inkMuted(context: Context): Int = context.getColor(R.color.ink_muted)

    fun inkFaint(context: Context): Int = context.getColor(R.color.ink_faint)

    fun surface(context: Context): Int = context.getColor(R.color.surface)

    fun hairline(context: Context): Int = context.getColor(R.color.hairline)

    fun trackOff(context: Context): Int = context.getColor(R.color.switch_track_off)

    fun accent(context: Context): Int = context.getColor(R.color.accent)

    fun mix(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f).roundToInt(),
            (Color.red(from) + (Color.red(to) - Color.red(from)) * f).roundToInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * f).roundToInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f).roundToInt()
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((255 * alpha.coerceIn(0f, 1f)).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    object Type {
        private val families: Set<String> by lazy {
            runCatching {
                android.graphics.fonts.SystemFonts.getAvailableFonts()
                    .map { it.file?.name ?: "" }
                    .toSet()
            }.getOrDefault(emptySet())
        }

        fun sans(weight: Int = 400): Typeface =
            Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)

        fun mono(): Typeface =
            if (families.any { it.startsWith("GeistMono") }) {
                Typeface.create("geist-mono", Typeface.NORMAL)
            } else {
                Typeface.MONOSPACE
            }
    }
}

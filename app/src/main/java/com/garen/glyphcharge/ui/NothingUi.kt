package com.garen.glyphcharge.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

object NothingUi {

    fun micro(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        typeface = GlyphTokens.Type.mono()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        letterSpacing = 0.16f
        setTextColor(GlyphTokens.inkMuted(context))
        includeFontPadding = false
        isSingleLine = true
    }

    fun label(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        typeface = GlyphTokens.Type.sans(400)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
        setTextColor(GlyphTokens.ink(context))
        includeFontPadding = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    fun value(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        typeface = GlyphTokens.Type.mono()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.06f
        setTextColor(GlyphTokens.inkMuted(context))
        includeFontPadding = false
        isSingleLine = true
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(GlyphTokens.hairline(context))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    fun dot(context: Context, diameter: Int): View = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(GlyphTokens.ink(context))
        }
        layoutParams = LinearLayout.LayoutParams(
            GlyphTokens.dp(context, diameter),
            GlyphTokens.dp(context, diameter)
        )
    }

    fun tintDot(dot: View, color: Int) {
        dot.backgroundTintList = ColorStateList.valueOf(color)
    }

    fun chevron(context: Context): TextView = TextView(context).apply {
        text = "\u203A"
        typeface = GlyphTokens.Type.sans(300)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        setTextColor(GlyphTokens.inkFaint(context))
        includeFontPadding = false
    }

    fun row(context: Context, left: View, right: View, minHeight: Int = 56): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = GlyphTokens.dp(context, minHeight)
            addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(right, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
}

class Section(context: Context, title: String, count: Int) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        setPadding(0, GlyphTokens.dp(context, 28), 0, 0)

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, GlyphTokens.dp(context, 10))
            addView(
                NothingUi.micro(context, title),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            if (count > 0) {
                addView(NothingUi.micro(context, String.format(Locale.US, "%02d", count)))
            }
        }

        addView(header)
        addView(NothingUi.divider(context))
    }

    fun addRow(row: View) {
        addView(row)
        addView(NothingUi.divider(context))
    }
}

class StatusCell(context: Context, caption: String) : LinearLayout(context) {

    val value: TextView = TextView(context)
    val dot: View = NothingUi.dot(context, 4)

    init {
        orientation = VERTICAL
        addView(NothingUi.micro(context, caption))
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, GlyphTokens.dp(context, 8), 0, 0)
            addView(dot)
            addView(
                value.apply {
                    typeface = GlyphTokens.Type.sans(400)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(GlyphTokens.ink(context))
                    includeFontPadding = false
                    isSingleLine = true
                    setPadding(GlyphTokens.dp(context, 7), 0, 0, 0)
                }
            )
        }
        addView(line)
    }
}

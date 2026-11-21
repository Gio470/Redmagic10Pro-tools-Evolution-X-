package com.elitedarkkaiser.redmagic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The card at the top of a feature page: its icon on an expressive shape, what it is doing now in
 * large type, a line of detail and live readings as chips. Tinted like Home's live cards.
 */
@SuppressLint("ViewConstructor")
internal class FeatureHero(context: Context, glyph: String, shape: CardShape) : LinearLayout(context) {
    private val m3 = M3(context)
    private val icon = ShapeIcon(context, glyph, shape, tertiary = false).apply { setOn(true, animate = false) }
    private val title = TextView(context).apply {
        m3.styleText(this, M3.Type.headlineSmall, m3.onSurface)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val detail = TextView(context).apply {
        m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        setPadding(0, m3.dp(4), 0, 0)
    }
    private val chips = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setPadding(0, m3.dp(14), 0, 0)
    }

    init {
        orientation = VERTICAL
        background = m3.cardShape(m3.tintedSurface, M3.Shape.extraLarge)
        setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(18))
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(M3.Space.sm); bottomMargin = m3.dp(M3.Space.sm) }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LayoutParams(m3.dp(56), m3.dp(56)).apply { marginEnd = m3.dp(16) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title)
                addView(detail)
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(chips)
    }

    /** [readings] become chips; blank ones are dropped and the row hides when there are none. */
    fun set(title: String, detail: String? = null, readings: List<String> = emptyList()) {
        if (this.title.text.toString() != title) this.title.text = title
        this.detail.text = detail.orEmpty()
        this.detail.visibility = if (detail.isNullOrBlank()) GONE else VISIBLE
        val shown = readings.map { it.trim() }.filter { it.isNotEmpty() }
        if (chips.childCount != shown.size) {
            chips.removeAllViews()
            shown.forEach { _ ->
                chips.addView(CardExtras.chip(context, "", background = m3.surfaceContainerHighest),
                    LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (chips.childCount > 0) marginStart = m3.dp(6)
                    })
            }
        }
        shown.forEachIndexed { i, text -> CardExtras.setChipText(chips.getChildAt(i), text) }
        chips.visibility = if (shown.isEmpty()) GONE else VISIBLE
    }
}

/** A short note under a page's controls, quieter than body text. */
internal fun pageNote(context: Context, text: String, glyph: String = Icons.INFO): LinearLayout {
    val m3 = M3(context)
    return LinearLayout(context).apply {
        gravity = Gravity.TOP
        setPadding(m3.dp(M3.Space.sm), m3.dp(M3.Space.md), m3.dp(M3.Space.sm), 0)
        addView(TextView(context).apply {
            this.text = glyph
            m3.styleGlyph(this, 14f, m3.onSurfaceVariant)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(m3.dp(20), m3.dp(20)).apply { marginEnd = m3.dp(10); topMargin = m3.dp(1) })
        addView(TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}

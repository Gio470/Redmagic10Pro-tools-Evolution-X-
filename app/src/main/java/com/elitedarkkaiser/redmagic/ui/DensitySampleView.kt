package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Illustrative controls that reflow inside the display density preview. */
internal class DensitySampleView(context: Context, fillViewport: Boolean = false) : LinearLayout(context) {
    private val m3 = M3(context)
    private val shell = GradientDrawable().apply { cornerRadius = m3.dp(24).toFloat() }
    private val heading = label("Redmagic Tools", M3.Type.titleMedium)
    private val caption = label("Your control center", M3.Type.bodySmall)
    private val mode = label("", M3.Type.labelSmall)
    private val tileSurfaces = mutableListOf<GradientDrawable>()
    private val tileTitles = mutableListOf<TextView>()
    private val tileDetails = mutableListOf<TextView>()
    private val tileIcons = mutableListOf<TextView>()
    private val iconSurfaces = mutableListOf<GradientDrawable>()
    private val primaryAction = label("Customize", M3.Type.labelLarge)
    private val secondaryAction = label("Settings", M3.Type.labelLarge)
    private val primaryShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val secondaryShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val nav = LinearLayout(context)
    private val navShape = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
    private val navLabels = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        background = shell
        setPadding(m3.dp(16), m3.dp(14), m3.dp(16), m3.dp(14))
        // This sample illustrates sizing; its controls are not interactive.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(heading)
            addView(caption)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mode.setPadding(m3.dp(8), m3.dp(4), m3.dp(8), m3.dp(4))
        header.addView(mode)
        addView(header)

        val tiles = LinearLayout(context)
        listOf(Triple("Cooling", "Fan controls", Icons.FAN),
            Triple("Triggers", "Game layouts", Icons.TRIGGERS)).forEachIndexed { index, (title, detail, glyph) ->
            val fill = GradientDrawable().apply { cornerRadius = m3.dp(20).toFloat() }
            val iconFill = GradientDrawable().apply { cornerRadius = m3.dp(12).toFloat() }
            tileSurfaces += fill
            iconSurfaces += iconFill
            tiles.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                background = fill
                setPadding(m3.dp(12), m3.dp(12), m3.dp(12), m3.dp(12))
                addView(TextView(context).apply {
                    text = glyph
                    m3.styleGlyph(this, 17f, m3.primary)
                    gravity = Gravity.CENTER
                    background = iconFill
                    tileIcons += this
                }, LayoutParams(m3.dp(32), m3.dp(32)))
                addView(label(title, M3.Type.titleSmall).also { tileTitles += it },
                    LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = m3.dp(8) })
                addView(label(detail, M3.Type.bodySmall).also { tileDetails += it })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = m3.dp(10)
            })
        }
        addView(tiles, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = m3.dp(12) })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            listOf(primaryAction to primaryShape, secondaryAction to secondaryShape).forEachIndexed { index, (text, fill) ->
                text.gravity = Gravity.CENTER
                text.background = fill
                addView(text, LayoutParams(0, m3.dp(40), 1f).apply {
                    if (index > 0) marginStart = m3.dp(8)
                })
            }
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(12) })

        if (fillViewport) addView(View(context), LayoutParams(1, 0, 1f))
        nav.background = navShape
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.setPadding(m3.dp(4), m3.dp(4), m3.dp(4), m3.dp(4))
        listOf("Home", "Hardware", "Software", "Settings").forEach { name ->
            nav.addView(label(name, M3.Type.labelSmall).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                navLabels += this
            }, LayoutParams(0, m3.dp(32), 1f))
        }
        addView(nav, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(12) })
        hideSampleChildren(this)
    }

    private fun label(value: String, style: M3.TypeStyle) = TextView(context).apply {
        text = value
        m3.styleText(this, style, m3.onSurface)
    }

    private fun hideSampleChildren(parent: ViewGroup) {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (child is ViewGroup) hideSampleChildren(child)
        }
    }

    /** Render using the app's current palette. */
    fun renderCurrentTheme() {
        renderColors(ColorUtils.calculateLuminance(m3.surface) < 0.5) { name ->
            when (name) {
                "background" -> m3.surface
                "on_surface" -> m3.onSurface
                "on_surface_variant" -> m3.onSurfaceVariant
                "primary" -> m3.primary
                "on_primary" -> m3.onPrimary
                "primary_container" -> m3.primaryContainer
                "on_primary_container" -> m3.onPrimaryContainer
                "secondary_container" -> m3.secondaryContainer
                "on_secondary_container" -> m3.onSecondaryContainer
                "surface_variant" -> m3.surfaceContainerHigh
                "outline_variant" -> m3.outlineVariant
                else -> error("Unknown sample color role: $name")
            }
        }
    }

    private fun renderColors(dark: Boolean, role: (String) -> Int) {
        val surface = role("background")
        val onSurface = role("on_surface")
        val secondaryText = role("on_surface_variant")
        val primary = role("primary")
        shell.setColor(surface)
        shell.setStroke(m3.dp(1), role("outline_variant"))
        heading.setTextColor(onSurface)
        caption.setTextColor(secondaryText)
        mode.text = if (dark) "Dark" else "Light"
        mode.setTextColor(primary)
        tileSurfaces.forEach { it.setColor(role("surface_variant")) }
        tileTitles.forEach { it.setTextColor(onSurface) }
        tileDetails.forEach { it.setTextColor(secondaryText) }
        tileIcons.forEach { it.setTextColor(role("on_primary_container")) }
        iconSurfaces.forEach { it.setColor(role("primary_container")) }
        primaryShape.setColor(primary)
        primaryAction.setTextColor(role("on_primary"))
        secondaryShape.setColor(role("secondary_container"))
        secondaryAction.setTextColor(role("on_secondary_container"))
        navShape.setColor(role("surface_variant"))
        navLabels.forEachIndexed { index, text ->
            text.setTextColor(if (index == 0) role("on_primary_container") else secondaryText)
            text.background = if (index == 0) GradientDrawable().apply {
                cornerRadius = m3.dp(16).toFloat()
                setColor(role("primary_container"))
            } else null
        }
        contentDescription = "${if (dark) "Dark" else "Light"} app preview. " +
            "Sample controls at the selected density."
    }
}


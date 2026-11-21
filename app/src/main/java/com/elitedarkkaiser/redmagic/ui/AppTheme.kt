package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.graphics.Color
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt

/** Material You / Material 3 Expressive palette and shape tokens. */
object AppTheme {
    /** Set once from RedMagicApplication.onCreate(); used to resolve theme colors. */
    var appContext: Context? = null

    private fun color(attr: Int, fallbackHex: String): Int {
        val context = appContext ?: return Color.parseColor(fallbackHex)
        // Through the generated palette first, like M3 does. A direct theme read returns the
        // colours baked into the XML now that nothing recolours the theme any more.
        return Palette.color(context, attr)
            ?: MaterialColors.getColor(context, attr, Color.parseColor(fallbackHex))
    }

    val bgColor: Int get() = color(com.google.android.material.R.attr.colorSurface, "#0A0D12")
    val panelColor: Int get() = color(com.google.android.material.R.attr.colorSurfaceContainerLow, "#121720")
    val accentColor: Int get() = color(com.google.android.material.R.attr.colorPrimary, "#8FA3BF")
    val textPrimary: Int get() = color(com.google.android.material.R.attr.colorOnSurface, "#E8EEF7")
    val textSecondary: Int get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant, "#9AA8BA")

    /** The M3 corner scale -- the same values as [M3.Shape], which is where new code should look. */
    object Shape {
        const val small = M3.Shape.small
        const val medium = M3.Shape.medium
        const val large = M3.Shape.large
        const val extraLarge = M3.Shape.extraLarge
        const val pill = M3.Shape.full
    }

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

}


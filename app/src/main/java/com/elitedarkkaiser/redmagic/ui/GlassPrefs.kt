package com.elitedarkkaiser.redmagic.ui

import android.content.Context

/**
 * How the glass bottom bars are drawn: the numbers behind the blur and the refraction, saved so
 * the Glass playground can be the screen that sets them.
 */
object GlassPrefs {

    /** One set of effect parameters. */
    data class Effects(
        /** Frosting. High values look soft but erase the detail refraction needs to be visible. */
        val blurRadiusDp: Float = 4f,
        /** How far in from the rim the refraction reaches -- the apparent thickness of the glass. */
        val refractionHeightDp: Float = 24f,
        /** How hard the rim bends what is behind it. */
        val refractionAmountDp: Float = 24f,
        /**
         * The colour wash over the material -- how much of [GlassTint]'s wash lands on the pane.
         * Not an opacity directly: each kind of pane maps this onto its own range, because a bar
         * can go all the way to clear and a sheet carrying body text cannot.
         */
        val tintAlpha: Float = 0.22f,
        /** Splits the refracted edge into red and blue fringes, like a real lens. */
        val dispersion: Boolean = false,
        /** Shading across the pane, so it reads as a slab rather than a hole. */
        val depthEffect: Boolean = false,
        /** The docked bar's top corners. The floating bar is a capsule and has no say in this. */
        val dockedCornerDp: Float = 22f
    )

    /** Default material, also used by Reset. */
    val DEFAULT = Effects(depthEffect = true)

    /** Slider ranges, kept here so the playground and any future editor agree on them. */
    const val MAX_BLUR_DP = 24f
    const val MAX_REFRACTION_HEIGHT_DP = 48f
    const val MAX_REFRACTION_AMOUNT_DP = 64f
    const val MAX_TINT = 0.6f
    const val MAX_DOCKED_CORNER_DP = 40f

    private const val PREFS = "glass"
    private const val KEY_BLUR = "blur_dp"
    private const val KEY_REFRACTION_HEIGHT = "refraction_height_dp"
    private const val KEY_REFRACTION_AMOUNT = "refraction_amount_dp"
    private const val KEY_TINT = "tint_alpha"
    private const val KEY_DISPERSION = "dispersion"
    private const val KEY_DEPTH = "depth_effect"
    private const val KEY_DOCKED_CORNER = "docked_corner_dp"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun effects(context: Context): Effects {
        val p = prefs(context)
        return Effects(
            blurRadiusDp = p.getFloat(KEY_BLUR, DEFAULT.blurRadiusDp),
            refractionHeightDp = p.getFloat(KEY_REFRACTION_HEIGHT, DEFAULT.refractionHeightDp),
            refractionAmountDp = p.getFloat(KEY_REFRACTION_AMOUNT, DEFAULT.refractionAmountDp),
            tintAlpha = p.getFloat(KEY_TINT, DEFAULT.tintAlpha),
            dispersion = p.getBoolean(KEY_DISPERSION, DEFAULT.dispersion),
            depthEffect = p.getBoolean(KEY_DEPTH, DEFAULT.depthEffect),
            dockedCornerDp = p.getFloat(KEY_DOCKED_CORNER, DEFAULT.dockedCornerDp)
        )
    }

    fun setEffects(context: Context, effects: Effects) {
        prefs(context).edit()
            .putFloat(KEY_BLUR, effects.blurRadiusDp)
            .putFloat(KEY_REFRACTION_HEIGHT, effects.refractionHeightDp)
            .putFloat(KEY_REFRACTION_AMOUNT, effects.refractionAmountDp)
            .putFloat(KEY_TINT, effects.tintAlpha)
            .putBoolean(KEY_DISPERSION, effects.dispersion)
            .putBoolean(KEY_DEPTH, effects.depthEffect)
            .putFloat(KEY_DOCKED_CORNER, effects.dockedCornerDp)
            .apply()
    }

}

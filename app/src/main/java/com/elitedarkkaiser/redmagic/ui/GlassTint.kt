package com.elitedarkkaiser.redmagic.ui

import com.google.android.material.color.utilities.Hct

/** The colour a pane of glass is washed with, and how opaque that wash is at a given Tint. */
object GlassTint {

    /** The floating bar, the docked bar and the settings button: from near-clear to near-solid. */
    const val BAR_MIN_ALPHA = 0.04f
    const val BAR_MAX_ALPHA = 0.78f

    /** Tone the wash sits at, relative to the surface it is drawn over. */
    private const val DARK_LIFT = 16.0
    private const val DARK_CEILING = 34.0
    private const val LIGHT_DROP = 11.0
    private const val LIGHT_FLOOR = 78.0

    /**
     * Bends the slider's travel towards the clear end. The top of the range has to reach a solid
     * frosted panel for the Frosted preset to mean anything, but the defaults live in the lower
     * third, and a straight line from clear to solid puts them halfway to opaque.
     */
    private const val CURVE = 1.35

    // Hct round-trips are not free and onDrawSurface runs every frame, so remember the last
    // answer. Two entries cover it: the palette only changes on a theme change, and both modes
    // never ask in the same frame.
    private var cachedKey = 0L
    private var cachedWash = 0

    /**
     * The opaque colour to wash a pane of [surface] with. Alpha in the result is 0xFF -- how much
     * of it lands is [alphaFor]'s business.
     */
    @Synchronized
    fun wash(surface: Int, isLight: Boolean): Int {
        val key = (surface.toLong() shl 1) or (if (isLight) 1L else 0L)
        if (key == cachedKey && cachedWash != 0) return cachedWash
        val hct = Hct.fromInt(surface)
        val tone = if (isLight) {
            (hct.tone - LIGHT_DROP).coerceAtLeast(LIGHT_FLOOR)
        } else {
            (hct.tone + DARK_LIFT).coerceAtMost(DARK_CEILING)
        }
        val result = Hct.from(hct.hue, hct.chroma, tone).toInt()
        cachedKey = key
        cachedWash = result
        return result
    }

    /**
     * Maps the stored Tint (0..[GlassPrefs.MAX_TINT]) onto the opacity range a given kind of pane
     * can use, along [CURVE].
     */
    fun alphaFor(tint: Float, minAlpha: Float, maxAlpha: Float): Float {
        val fraction = (tint / GlassPrefs.MAX_TINT).coerceIn(0f, 1f)
        return minAlpha + Math.pow(fraction.toDouble(), CURVE).toFloat() * (maxAlpha - minAlpha)
    }

    /** [wash] and [alphaFor] together, as one ARGB colour ready to draw. */
    fun colorFor(surface: Int, isLight: Boolean, tint: Float, minAlpha: Float, maxAlpha: Float): Int {
        val alpha = (alphaFor(tint, minAlpha, maxAlpha) * 255f).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (wash(surface, isLight) and 0x00FFFFFF)
    }

    /** The wash for a bottom bar or the settings button. */
    fun bar(surface: Int, isLight: Boolean, tint: Float): Int =
        colorFor(surface, isLight, tint, BAR_MIN_ALPHA, BAR_MAX_ALPHA)
}

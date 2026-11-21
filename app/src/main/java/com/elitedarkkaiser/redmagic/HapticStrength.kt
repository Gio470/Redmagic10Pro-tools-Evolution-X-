package com.elitedarkkaiser.redmagic

import android.content.Context

/** How hard the trigger haptics hit, as a percentage. */
object HapticStrength {

    private const val FILE = "triggers"
    private const val KEY = "haptic_strength"

    const val DEFAULT = 100

    /**
     * Below this the actuator twitches without being felt, so a setting that isn't [OFF] never
     * scales under it -- a slider you can move without anything changing reads as broken.
     */
    private const val FLOOR_GAIN = 28

    const val OFF = 0

    /** 0 (off) to 100 (what the haptics did before this slider existed). */
    fun get(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY, DEFAULT)
            .coerceIn(0, 100)

    fun set(context: Context, percent: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY, percent.coerceIn(0, 100))
            .apply()
    }

    /** The gain to actually write, given what a haptic asks for at full strength. */
    fun scale(baseGain: Int, percent: Int): Int {
        if (percent <= OFF) return 0
        val scaled = baseGain * percent / 100
        return scaled.coerceIn(FLOOR_GAIN, 255)
    }

    /** For the slider's value chip. */
    fun label(percent: Int): String = if (percent <= OFF) "Off" else "$percent%"
}

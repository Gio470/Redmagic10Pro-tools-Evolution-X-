package com.elitedarkkaiser.redmagic.parts

import android.content.Context
import com.elitedarkkaiser.redmagic.HardwareController

/** Fan writers serialize with the temporary minimum so an older off-write cannot win a race. */
internal object CoolingOverrides {
    private val state = FanOverrideState()
    val floor: Int get() = state.floor
    private var context: Context? = null
    @Synchronized fun <T> withLevel(level: Int, write: (Int) -> T): T {
        val effective = state.request(level)
        remember()
        return write(effective)
    }
    @Synchronized fun <T> withEnabled(on: Boolean, write: (Int?) -> T): T {
        val effective = state.enabled(on)
        remember()
        return write(effective)
    }
    @Synchronized fun snapshot(enabled: Boolean, level: Int): Pair<Boolean, Int> = state.snapshot(enabled, level)
    private fun remember() {
        if (floor > 0) context?.let { PartsPrefs.prefs(it).edit().putInt("fan_override_base", state.base).apply() }
    }
    /** Off the UI thread. Snapshot the motor before applying the first minimum. */
    @Synchronized fun setFloor(c: Context, value: Int) {
        val next = value.coerceIn(0, 5)
        if (next == floor) return
        val prefs = PartsPrefs.prefs(c)
        val snapshot = if (floor == 0 && next > 0) {
            context = c.applicationContext
            val base = if (prefs.getBoolean("fan_override_active", false)) prefs.getInt("fan_override_base", 0)
                else if (HardwareController.isFanEnabled()) HardwareController.readFanLevel() ?: 0 else 0
            prefs.edit().putBoolean("fan_override_active", true).putInt("fan_override_base", base).apply()
            base
        } else null
        HardwareController.writeFanEffective(state.transition(next, snapshot))
        if (next == 0) { prefs.edit().putBoolean("fan_override_active", false).apply(); context = null }
    }
    @Synchronized fun reapply(): Boolean = floor == 0 || HardwareController.writeFanEffective(maxOf(state.base, floor), force = true)
    @Synchronized fun recover(c: Context) {
        val p = PartsPrefs.prefs(c)
        if (floor == 0 && p.getBoolean("fan_override_active", false)) {
            if (HardwareController.writeFanEffective(p.getInt("fan_override_base", 0)))
                p.edit().putBoolean("fan_override_active", false).apply()
        }
    }
}

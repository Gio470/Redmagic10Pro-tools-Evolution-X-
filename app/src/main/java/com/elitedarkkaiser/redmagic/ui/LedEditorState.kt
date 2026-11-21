package com.elitedarkkaiser.redmagic.ui

import com.elitedarkkaiser.redmagic.state.LedState

/** Normal lights retain their card master; automatic profiles also offer an Off animation. */
internal class LedEditorState(initial: LedState, private val fan: Boolean,
    private val allowOff: Boolean, private val onChanged: (LedState) -> Unit) {
    var state = initial
        private set

    fun animation(effect: String) {
        if (effect == "off") {
            if (allowOff) change(state.copy(enabled = false))
        } else change(state.copy(enabled = if (allowOff) true else state.enabled,
            effect = effect, color = state.color.takeIf { it >= 0 } ?: if (fan) 5 else 1))
    }

    fun colour(id: Int) {
        if (allowOff && !state.enabled) return
        change(state.copy(color = id, effect = if (state.effect.startsWith("preset:")) {
            if (fan && id >= 10) "flow" else "steady"
        } else state.effect))
    }

    fun preset(value: String) {
        if (!fan || (allowOff && !state.enabled)) return
        change(state.copy(effect = "preset:$value", color = -1))
    }

    private fun change(next: LedState) {
        if (state == next) return
        state = next
        onChanged(next)
    }
}

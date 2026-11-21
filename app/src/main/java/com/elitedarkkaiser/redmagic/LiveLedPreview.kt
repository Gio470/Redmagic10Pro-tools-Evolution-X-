package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState

/** Picker lighting, gated by the feature's current master, never by a second preference. */
internal class LiveLedPreview(
    private val enabled: () -> Boolean,
    private val apply: (Zone, LedState) -> Unit,
    private val acquire: () -> Unit = {},
    private val restore: () -> Unit = {},
    private val dispose: () -> Unit = {}
) : AutoCloseable {
    enum class Zone { FAN, LOGO, SHOULDER }
    private val shown = mutableMapOf<Zone, LedState>()
    private val requested = mutableMapOf<Zone, LedState>()
    private var active = false
    private var closed = false
    private var paused = false

    fun update(zone: Zone, state: LedState) {
        if (closed) return
        requested[zone] = state
        if (paused) return
        if (!enabled()) { release(); return }
        if (shown[zone] == state) return
        if (!active) { acquire(); active = true }
        apply(zone, state)
        shown[zone] = state
    }

    fun pause() {
        paused = true
        release()
    }

    fun resume() {
        if (closed) return
        paused = false
        requested.toMap().forEach { (zone, state) -> update(zone, state) }
    }

    private fun release() {
        if (!active) return
        active = false
        shown.clear()
        restore()
    }

    override fun close() {
        if (closed) return
        closed = true
        try { release() } finally { dispose() }
    }
}

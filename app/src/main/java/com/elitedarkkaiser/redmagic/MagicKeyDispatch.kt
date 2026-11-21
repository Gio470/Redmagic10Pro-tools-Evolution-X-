package com.elitedarkkaiser.redmagic

import java.util.concurrent.atomic.AtomicInteger

/** Serial action work, invalidated immediately when settings change or the service stops. */
internal class MagicKeyDispatch(private val schedule: (() -> Unit) -> Unit,
    private val current: () -> MagicKeyConfig?, private val change: (MagicKeyConfig, Boolean) -> Unit,
    private val release: () -> Unit) {
    private val generation = AtomicInteger()
    @Volatile private var configured: MagicKeyConfig? = null
    @Volatile private var stopped = false

    fun configure(configuration: MagicKeyConfig) {
        if (stopped || configured == configuration) return
        configured = null
        generation.incrementAndGet()
        schedule(release)
        configured = configuration
    }

    fun event(on: Boolean) {
        if (stopped) return
        val config = configured ?: return
        if (current() != config) return
        val token = generation.get()
        schedule {
            if (!stopped && token == generation.get() && configured == config && current() == config) change(config, on)
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        configured = null
        generation.incrementAndGet()
        schedule(release)
    }
}

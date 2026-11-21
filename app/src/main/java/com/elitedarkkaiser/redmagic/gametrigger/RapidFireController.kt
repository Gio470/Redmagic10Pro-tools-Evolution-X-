package com.elitedarkkaiser.redmagic.gametrigger

/** Cancellable tap sequences. All callbacks and cancellation share the same lock. */
internal class RapidFireController(
    private val scheduler: Scheduler,
    private val lock: Any = Any(),
    private val onFailure: (Throwable) -> Unit = {}
) {
    fun interface Cancellation { fun cancel() }
    fun interface Scheduler { fun schedule(delayMs: Long, task: () -> Unit): Cancellation }

    data class Timing(val holdMs: Long, val gapMs: Long)
    companion object {
        val STANDARD = Timing(40, 40)
        // Same 80 ms tap period, but only one fifth of the previous pause in aim updates.
        val SMOOTH_AIM = Timing(72, 8)
    }

    private class Session(val side: Int, var remaining: Int,
        val pulse: (Boolean) -> Unit, val stopped: () -> Unit, val timing: Timing) {
        var down = false
        var pending: Cancellation? = null
    }
    private val sessions = arrayOfNulls<Session>(2)

    fun start(side: Int, taps: Int, pulse: (Boolean) -> Unit, stopped: () -> Unit = {},
        timing: Timing = STANDARD) = synchronized(lock) {
        require(side in 0..1 && taps in 1..100)
        require(timing.holdMs > 0 && timing.gapMs > 0)
        stop(side)
        val session = Session(side, taps, pulse, stopped, timing)
        sessions[side] = session
        try {
            down(session)
        } catch (e: Throwable) {
            runCatching { finish(session) }.onFailure { if (it !== e) e.addSuppressed(it) }
            throw e
        }
    }

    /** Returns only after any current contact is up; stale queued tasks cannot press again. */
    fun stop(side: Int) = synchronized(lock) { sessions[side]?.let(::finish); Unit }
    fun stopAll(): Unit = synchronized(lock) {
        var failure: Throwable? = null
        sessions.indices.forEach { side ->
            runCatching { stop(side) }.onFailure { error ->
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun current(session: Session) = sessions[session.side] === session

    private fun down(session: Session) {
        if (!current(session)) return
        session.down = true
        session.pulse(true)
        if (current(session)) schedule(session, session.timing.holdMs) { up(session) }
    }

    private fun up(session: Session) {
        if (!current(session)) return
        session.down = false
        session.pulse(false)
        if (!current(session)) return
        if (--session.remaining == 0) finish(session)
        else schedule(session, session.timing.gapMs) { down(session) }
    }

    private fun schedule(session: Session, delayMs: Long, action: () -> Unit) {
        session.pending = scheduler.schedule(delayMs) {
            synchronized(lock) {
                if (!current(session)) return@synchronized
                try { action() }
                catch (e: Throwable) {
                    runCatching { finish(session) }.onFailure { if (it !== e) e.addSuppressed(it) }
                    onFailure(e)
                }
            }
        }
    }

    private fun finish(session: Session) {
        if (!current(session)) return
        sessions[session.side] = null
        session.pending?.cancel()
        try {
            if (session.down) { session.down = false; session.pulse(false) }
        } finally {
            session.stopped()
        }
    }
}

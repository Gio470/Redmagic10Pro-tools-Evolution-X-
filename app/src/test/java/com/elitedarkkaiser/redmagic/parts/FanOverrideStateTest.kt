package com.elitedarkkaiser.redmagic.parts

import org.junit.Assert.*
import org.junit.Test

class FanOverrideStateTest {
    @Test fun leavingAGameRestoresTheFanThatWasOff() {
        val state = FanOverrideState()
        assertEquals(3, state.transition(3, 0))
        assertEquals(0, state.transition(0))
    }
    @Test fun manualRequestsDuringTheOverrideAreRestoredOnExit() {
        val state = FanOverrideState()
        state.transition(5, 3)
        assertEquals(5, state.request(2))
        assertEquals(2, state.transition(0))
        state.transition(5, 2)
        assertEquals(5, state.enabled(false))
        assertEquals(0, state.transition(0))
    }
    @Test fun oneOverrideEndingDoesNotEraseTheOtherOrReplaceTheBaseline() {
        val state = FanOverrideState()
        assertEquals(4, state.transition(3, 4))
        assertEquals(5, state.transition(5))
        assertEquals(4, state.transition(3))
        assertEquals(4, state.transition(0))
    }
    @Test fun disablingAndReenablingDuringForcedCoolingRecallsTheRequestedLevel() {
        val state = FanOverrideState()
        state.transition(5, 4)
        state.enabled(false)
        assertEquals(5, state.enabled(true))
        assertEquals(4, state.transition(0))
        assertNull(state.enabled(false))
    }
    @Test fun callPauseSnapshotsAndRestoresTheUnderlyingRequestInsteadOfTheForcedSpeed() {
        val state = FanOverrideState()
        state.transition(5, 0)
        val beforeCall = state.snapshot(true, 5)
        assertEquals(false to 0, beforeCall)
        state.enabled(false)
        state.request(if (beforeCall.first) beforeCall.second else 0)
        assertEquals(0, state.transition(0))
        state.transition(5, 3)
        assertEquals(true to 3, state.snapshot(true, 5))
    }
}

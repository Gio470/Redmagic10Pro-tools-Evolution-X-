package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class MagicKeyDispatchTest {
    private class Harness {
        val queued = ArrayDeque<() -> Unit>()
        val changes = mutableListOf<Boolean>()
        var current: MagicKeyConfig? = MagicKeyConfig(MagicKeyMode.CAMERA, null, false)
        var releases = 0
        val dispatch = MagicKeyDispatch({ queued.addLast(it) }, { current }, { _, on -> changes += on }, { releases++ })
        fun drain() { while (queued.isNotEmpty()) queued.removeFirst()() }
    }

    @Test fun reopeningTheAppWithTheSameMappingDoesNotReleaseAHeldAction() {
        val h = Harness()
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.dispatch.event(true)
        h.drain()
        assertEquals(listOf(true), h.changes)
        val releases = h.releases
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.drain()
        assertEquals(releases, h.releases)
    }
    @Test fun aChangedPreferenceInvalidatesQueuedActionsBeforeTheServiceReceivesItsRestart() {
        val h = Harness()
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.dispatch.event(true)
        h.current = MagicKeyConfig(MagicKeyMode.FLASHLIGHT, null, false)
        h.drain()
        assertTrue(h.changes.isEmpty())
        h.dispatch.event(true)
        h.drain()
        assertTrue(h.changes.isEmpty())
    }
    @Test fun remappingCleansUpThePreviousActionAndIgnoresItsOldEvents() {
        val h = Harness()
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.dispatch.event(true)
        h.current = MagicKeyConfig(MagicKeyMode.FLASHLIGHT, null, false)
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.FLASHLIGHT, null, false))
        h.dispatch.event(true)
        h.drain()
        assertEquals(listOf(true), h.changes)
        assertEquals(2, h.releases)
    }
    @Test fun disablingStopsQueuedActionsAndStillRunsCleanup() {
        val h = Harness()
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.dispatch.event(true)
        h.current = null
        h.dispatch.stop()
        h.dispatch.event(true)
        h.drain()
        assertTrue(h.changes.isEmpty())
        assertEquals(2, h.releases)
    }
    @Test fun releasingTheSliderReachesTheActionCleanup() {
        val h = Harness()
        h.dispatch.configure(MagicKeyConfig(MagicKeyMode.CAMERA, null, false))
        h.dispatch.event(true)
        h.dispatch.event(false)
        h.drain()
        assertEquals(listOf(true, false), h.changes)
    }
    @Test fun changingOnlyTheChosenAppStillInvalidatesAnOldLaunch() {
        val h = Harness()
        val old = MagicKeyConfig(MagicKeyMode.APP, "com.game.one", false)
        h.current = old
        h.dispatch.configure(old)
        h.dispatch.event(true)
        h.current = old.copy(app = "com.game.two")
        h.drain()
        assertTrue(h.changes.isEmpty())
    }

}

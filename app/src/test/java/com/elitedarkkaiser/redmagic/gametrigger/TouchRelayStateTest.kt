package com.elitedarkkaiser.redmagic.gametrigger

import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.ABS_MT_POSITION_X
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.ABS_MT_POSITION_Y
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.ABS_MT_SLOT
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.ABS_MT_TOUCH_MAJOR
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.ABS_MT_TRACKING_ID
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.Axis
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TouchRelayStateTest {
    // The actual gdix_input_agent ranges supplied by the NX789J user.
    private val abs = mapOf(
        ABS_MT_SLOT to Axis(0, 9),
        ABS_MT_TOUCH_MAJOR to Axis(0, 4079),
        ABS_MT_POSITION_X to Axis(0, 19455),
        ABS_MT_POSITION_Y to Axis(0, 43007),
        ABS_MT_TRACKING_ID to Axis(0, 65535)
    )
    private val out = ByteArrayOutputStream()
    private val relay = TouchRelayState(abs, 10, out)

    private data class Event(val type: Int, val code: Int, val value: Int)
    private fun drain(): List<Event> {
        val buffer = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        out.reset()
        val events = mutableListOf<Event>()
        while (buffer.hasRemaining()) {
            buffer.position(buffer.position() + 16)
            events += Event(buffer.short.toInt(), buffer.short.toInt(), buffer.int)
        }
        return events
    }

    /** Apply emitted events as a protocol-B receiver would, preserving unchanged slots. */
    private class Receiver {
        val contacts = mutableMapOf<Int, MutableMap<Int, Int>>()
        var touching = false
        private var slot = 0
        fun accept(events: List<Event>) {
            events.forEach { event ->
                if (event.type == 3) {
                    if (event.code == ABS_MT_SLOT) slot = event.value
                    else contacts.getOrPut(slot) { mutableMapOf() }[event.code] = event.value
                }
                if (event.type == 1 && event.code == 0x14a) touching = event.value == 1
            }
        }
        fun id(slot: Int) = contacts[slot]?.get(ABS_MT_TRACKING_ID) ?: -1
    }

    private fun finger(slot: Int, id: Int, x: Int = 4000, y: Int = 8000) = listOf(
        TouchRelayState.Update(slot, ABS_MT_TRACKING_ID, id),
        TouchRelayState.Update(slot, ABS_MT_POSITION_X, x),
        TouchRelayState.Update(slot, ABS_MT_POSITION_Y, y),
        TouchRelayState.Update(slot, ABS_MT_TOUCH_MAJOR, 71)
    )

    @Test fun aHoldBeforeCaptureIsQueuedUntilFingersCanUseTheSameDevice() {
        relay.contact(0, 9155, 34807, true)
        assertEquals(0, out.size())
        relay.capture()
        val receiver = Receiver().apply { accept(drain()) }
        assertTrue(receiver.touching)
        assertTrue(receiver.id(10) >= 0)
        assertEquals(9155, receiver.contacts.getValue(10)[ABS_MT_POSITION_X])
        assertEquals(34807, receiver.contacts.getValue(10)[ABS_MT_POSITION_Y])
    }

    @Test fun aTapCompletedWhileWaitingDoesNotFireAfterCapture() {
        relay.contact(0, 10, 20, true)
        relay.contact(0, 10, 20, false)
        relay.capture()
        assertTrue(drain().isEmpty())
    }

    @Test fun holdFireThenAimThenLiftKeepsBothContactsOnTheVirtualDevice() {
        relay.capture()
        relay.contact(0, 9155, 34807, true)
        val receiver = Receiver().apply { accept(drain()) }
        val triggerId = receiver.id(10)
        relay.sourceFrame(finger(0, 123))
        receiver.accept(drain())
        val fingerId = receiver.id(0)
        assertTrue(fingerId >= 0)
        assertNotEquals(triggerId, fingerId)
        assertEquals(triggerId, receiver.id(10))
        relay.sourceFrame(listOf(TouchRelayState.Update(0, ABS_MT_POSITION_X, 10000)))
        receiver.accept(drain())
        assertEquals(10000, receiver.contacts.getValue(0)[ABS_MT_POSITION_X])
        assertEquals(fingerId, receiver.id(0))
        assertEquals(triggerId, receiver.id(10))
        assertTrue(relay.diagnostics().contains("overlapFrames=2"))

        relay.sourceFrame(listOf(TouchRelayState.Update(0, ABS_MT_TRACKING_ID, -1)))
        receiver.accept(drain())
        assertEquals(-1, receiver.id(0))
        assertTrue(receiver.touching)
        assertEquals(triggerId, receiver.id(10))
        relay.contact(0, 9155, 34807, false)
        receiver.accept(drain())
        assertFalse(receiver.touching)
    }

    @Test fun liftingTheTriggerLeavesAnAimingFingerDown() {
        relay.capture()
        relay.sourceFrame(finger(0, 123))
        relay.contact(1, 10, 20, true)
        val receiver = Receiver().apply { accept(drain()) }
        val fingerId = receiver.id(0)
        relay.contact(1, 10, 20, false)
        receiver.accept(drain())
        assertEquals(fingerId, receiver.id(0))
        assertEquals(-1, receiver.id(11))
        assertTrue(receiver.touching)
    }

    @Test fun restartingAfterReleaseDoesNotReplayOldContacts() {
        relay.capture()
        relay.sourceFrame(finger(0, 123))
        relay.contact(0, 10, 20, true)
        val receiver = Receiver().apply { accept(drain()) }
        relay.release()
        receiver.accept(drain())
        assertEquals(-1, receiver.id(0))
        assertEquals(-1, receiver.id(10))
        assertFalse(receiver.touching)
        relay.capture()
        assertTrue(drain().isEmpty())
    }

    @Test fun aNewContactAtZeroIncludesItsFullPositionAndSize() {
        relay.capture()
        relay.sourceFrame(finger(0, 123, 0, 0))
        drain()
        relay.sourceFrame(listOf(TouchRelayState.Update(0, ABS_MT_TRACKING_ID, -1)))
        drain()
        relay.sourceFrame(finger(0, 124, 0, 0))
        val events = drain()
        assertTrue(events.contains(Event(3, ABS_MT_POSITION_X, 0)))
        assertTrue(events.contains(Event(3, ABS_MT_POSITION_Y, 0)))
        assertTrue(events.contains(Event(3, ABS_MT_TOUCH_MAJOR, 71)))
        assertEquals(1, events.count { it.type == 0 && it.code == 0 })
        assertEquals(Event(0, 0, 0), events.last())
    }

    @Test fun identicalTrackingIdsFromTwoSourcesGetDifferentVirtualIds() {
        val combined = TouchRelayState(abs, 20, out)
        combined.capture()
        combined.sourceFrame(finger(0, 0) + finger(10, 0))
        val receiver = Receiver().apply { accept(drain()) }
        assertTrue(receiver.id(0) >= 0)
        assertTrue(receiver.id(10) >= 0)
        assertNotEquals(receiver.id(0), receiver.id(10))
    }
    private fun dragSetup() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 16000, 40000, true)
        relay.sourceFrame(finger(0, 1, 3000, 20000) + finger(1, 2, 12000, 20000))
    }

    private fun frameGroups(events: List<Event>): List<List<Event>> {
        val groups = mutableListOf<List<Event>>()
        var start = 0
        events.forEachIndexed { i, event ->
            if (event.type == 0 && event.code == 0) {
                groups += events.subList(start, i + 1)
                start = i + 1
            }
        }
        assertEquals(events.size, start)
        return groups
    }

    @Test fun dragAimMovesTheHeldShootContactWhileTheMovementFingerKeepsItsId() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        val leftId = receiver.id(0)
        val shotId = receiver.id(11)
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320),
            TouchRelayState.Update(1, ABS_MT_POSITION_Y, 20160)))
        receiver.accept(drain())
        assertEquals(leftId, receiver.id(0))
        assertEquals(-1, receiver.id(1))
        assertEquals(shotId, receiver.id(11))
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        assertEquals(40160, receiver.contacts.getValue(11)[ABS_MT_POSITION_Y])
        assertTrue(relay.diagnostics().contains("aimMoves=1"))
    }

    @Test fun rightSideTapsAndSmallFingerJitterStillPassThroughNormally() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        val aimId = receiver.id(1)
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12016)))
        receiver.accept(drain())
        assertEquals(aimId, receiver.id(1))
        assertEquals(16000, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_TRACKING_ID, -1)))
        receiver.accept(drain())
        assertEquals(-1, receiver.id(1))
        assertTrue(receiver.id(11) >= 0)
        assertTrue(relay.diagnostics().contains("aimMoves=0"))
    }

    @Test fun aMovementFingerThatCrossesTheScreenMiddleNeverBecomesTheAimFinger() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        val leftId = receiver.id(0)
        relay.sourceFrame(listOf(TouchRelayState.Update(0, ABS_MT_POSITION_X, 15000)))
        receiver.accept(drain())
        assertEquals(leftId, receiver.id(0))
        assertEquals(15000, receiver.contacts.getValue(0)[ABS_MT_POSITION_X])
        assertEquals(16000, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

    @Test fun releaseShootReturnsTheAimingFingerWithoutLeavingAFireContactDown() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        receiver.accept(drain())
        assertEquals(-1, receiver.id(1))
        relay.contact(1, 16000, 40000, false)
        receiver.accept(drain())
        assertEquals(-1, receiver.id(11))
        assertTrue(receiver.id(0) >= 0 && receiver.id(1) >= 0)
        assertEquals(12320, receiver.contacts.getValue(1)[ABS_MT_POSITION_X])
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12640)))
        receiver.accept(drain())
        assertEquals(12640, receiver.contacts.getValue(1)[ABS_MT_POSITION_X])
    }

    @Test fun aNewSwipeRestartsShootBeforeResettingItsPositionSoTheCameraDoesNotReverse() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        val leftId = receiver.id(0)
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        receiver.accept(drain())
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_TRACKING_ID, -1)))
        receiver.accept(drain())
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        relay.sourceFrame(finger(1, 3, 13000, 20000))
        receiver.accept(drain())
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 13320)))
        val frames = frameGroups(drain())
        assertEquals(3, frames.size)
        receiver.accept(frames[0])
        assertEquals(-1, receiver.id(11))
        assertEquals(leftId, receiver.id(0))
        receiver.accept(frames[1])
        assertTrue(receiver.id(11) >= 0)
        assertEquals(16000, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        receiver.accept(frames[2])
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        assertEquals(-1, receiver.id(1))
        assertEquals(leftId, receiver.id(0))
        assertTrue(relay.diagnostics().contains("aimResets=1"))
    }

    @Test fun reachingThePanelEdgeRestartsTheShotRatherThanWarpingAHeldContact() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 19400, 40000, true)
        relay.sourceFrame(finger(1, 1, 12000, 20000))
        val receiver = Receiver().apply { accept(drain()) }
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        val frames = frameGroups(drain())
        assertEquals(3, frames.size)
        receiver.accept(frames[0])
        assertEquals(-1, receiver.id(11))
        receiver.accept(frames[1])
        assertTrue(receiver.id(11) >= 0)
        assertEquals(19400, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        receiver.accept(frames[2])
        assertEquals(19455, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

    @Test fun anAimingFingerAlreadyDownWhenShootIsPressedUsesOnlyItsNewMovement() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.sourceFrame(finger(1, 1, 11000, 20000))
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12000)))
        relay.contact(1, 16000, 40000, true)
        val receiver = Receiver().apply { accept(drain()) }
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        receiver.accept(drain())
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

    @Test fun dragAimUsesTheDisplayRightHalfAndMatchesFingerDeltasInAllRotations() {
        for (rotation in 0..3) {
            val rotated = rotation == 1 || rotation == 3
            val w = if (rotated) 2688 else 1216
            val h = if (rotated) 1216 else 2688
            val testRelay = TouchRelayState(abs, 10, out)
            testRelay.configureDragAim(1, rotation, w, h)
            testRelay.capture()
            fun raw(x: Int, y: Int) = TouchInjectorMath.toRaw(x, y, rotation, w, h,
                abs.getValue(ABS_MT_POSITION_X), abs.getValue(ABS_MT_POSITION_Y))
            val start = raw(w * 3 / 4, h / 2)
            val end = raw(w * 3 / 4 + 20, h / 2 + 12)
            val shot = raw(w * 4 / 5, h * 3 / 4)
            val left = raw(w / 4, h / 2)
            testRelay.contact(1, shot.first, shot.second, true)
            testRelay.sourceFrame(finger(0, 1, left.first, left.second) + finger(1, 2, start.first, start.second))
            val receiver = Receiver().apply { accept(drain()) }
            val leftId = receiver.id(0)
            testRelay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, end.first),
                TouchRelayState.Update(1, ABS_MT_POSITION_Y, end.second)))
            receiver.accept(drain())
            assertEquals("rotation $rotation aim finger must be merged into Shoot", -1, receiver.id(1))
            assertEquals(shot.first + end.first - start.first, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
            assertEquals(shot.second + end.second - start.second, receiver.contacts.getValue(11)[ABS_MT_POSITION_Y])
            assertEquals(leftId, receiver.id(0))
            testRelay.release()
            drain()
        }
    }

    @Test fun turningDragAimOffRestoresPhysicalFingersAndEndsTheHeldShootAction() {
        dragSetup()
        val receiver = Receiver().apply { accept(drain()) }
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        receiver.accept(drain())
        relay.configureDragAim(null, 0, 1216, 2688)
        receiver.accept(drain())
        assertEquals(-1, receiver.id(11))
        assertTrue(receiver.id(1) >= 0)
    }

    @Test fun rapidAimKeepsTheSameAimingFingerAcrossTapGapsAndReplaysGapMovementAfterDown() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 16000, 40000, true, keepAim = true)
        relay.sourceFrame(finger(0, 1, 3000, 20000) + finger(1, 2, 12000, 20000))
        val receiver = Receiver().apply { accept(drain()) }
        val leftId = receiver.id(0)
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        receiver.accept(drain())
        assertEquals(-1, receiver.id(1))
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])

        relay.contact(1, 16000, 40000, false, keepAim = true)
        receiver.accept(drain())
        assertEquals(-1, receiver.id(11))
        assertEquals(-1, receiver.id(1))
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12640)))
        receiver.accept(drain())
        assertEquals(-1, receiver.id(1))

        relay.contact(1, 16000, 40000, true, keepAim = true)
        val frames = frameGroups(drain())
        assertEquals(2, frames.size)
        receiver.accept(frames[0])
        assertTrue(receiver.id(11) >= 0)
        assertEquals(16000, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        receiver.accept(frames[1])
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        assertEquals(leftId, receiver.id(0))
        assertEquals(-1, receiver.id(1))
    }

    @Test fun releasingRapidAimDuringTheGapRestoresTheFingerAndDiscardsPendingMovement() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 16000, 40000, true, keepAim = true)
        relay.sourceFrame(finger(1, 1, 12000, 20000))
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        val receiver = Receiver().apply { accept(drain()) }
        relay.contact(1, 16000, 40000, false, keepAim = true)
        receiver.accept(drain())
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12640)))
        receiver.accept(drain())
        relay.contact(1, 16000, 40000, false) // Physical shoulder released, not just the pulse.
        receiver.accept(drain())
        assertEquals(-1, receiver.id(11))
        assertTrue(receiver.id(1) >= 0)
        assertEquals(12640, receiver.contacts.getValue(1)[ABS_MT_POSITION_X])
        relay.contact(1, 16000, 40000, true, keepAim = true)
        receiver.accept(drain())
        assertEquals("the previous hold must not replay its buffered delta", 16000,
            receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

    @Test fun aSwipeStartingBetweenRapidTapsIsAttachedWithoutLeakingAnotherAimPointer() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 16000, 40000, true, keepAim = true)
        relay.contact(1, 16000, 40000, false, keepAim = true)
        relay.sourceFrame(finger(1, 1, 12000, 20000))
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        val receiver = Receiver().apply { accept(drain()) }
        assertEquals(-1, receiver.id(1))
        assertEquals(-1, receiver.id(11))
        relay.contact(1, 16000, 40000, true, keepAim = true)
        receiver.accept(drain())
        assertTrue(receiver.id(11) >= 0)
        assertEquals(-1, receiver.id(1))
        assertEquals(16320, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

    @Test fun cancellingTheTapControllerBetweenPulsesEndsRapidAimAndCannotPressAgain() {
        val queued = mutableListOf<() -> Unit>()
        val controller = RapidFireController(RapidFireController.Scheduler { _, action ->
            queued += action
            RapidFireController.Cancellation { /* Deliberately leave the stale action runnable. */ }
        })
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        controller.start(1, 100,
            pulse = { relay.contact(1, 16000, 40000, it, keepAim = true) },
            stopped = { relay.contact(1, 16000, 40000, false) })
        relay.sourceFrame(finger(0, 1, 3000, 20000) + finger(1, 2, 12000, 20000))
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        val receiver = Receiver().apply { accept(drain()) }
        val leftId = receiver.id(0)
        queued[0]() // First pulse ends; the next down is now queued.
        receiver.accept(drain())
        assertEquals(-1, receiver.id(11))
        assertEquals(-1, receiver.id(1))
        controller.stop(1) // Shoulder released during the gap.
        receiver.accept(drain())
        assertTrue(receiver.id(1) >= 0)
        assertEquals(leftId, receiver.id(0))
        assertEquals(-1, receiver.id(11))
        queued.toList().forEach { it() }
        assertTrue("old timer callbacks must not fire or move the camera", drain().isEmpty())
    }

    @Test fun rapidAimAtTheScreenEdgeDoesNotInsertUnscheduledExtraTaps() {
        relay.configureDragAim(1, 0, 1216, 2688)
        relay.capture()
        relay.contact(1, 19400, 40000, true, keepAim = true)
        relay.sourceFrame(finger(1, 1, 12000, 20000))
        val receiver = Receiver().apply { accept(drain()) }
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12320)))
        val edge = drain()
        assertFalse("edge motion must not lift/repress Shoot during a rapid pulse",
            edge.any { it.code == ABS_MT_TRACKING_ID && it.value >= 0 })
        receiver.accept(edge)
        assertEquals(19455, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
        relay.contact(1, 19400, 40000, false, keepAim = true)
        receiver.accept(drain())
        relay.sourceFrame(listOf(TouchRelayState.Update(1, ABS_MT_POSITION_X, 12640)))
        drain()
        relay.contact(1, 19400, 40000, true, keepAim = true)
        val next = drain()
        assertEquals("exactly one DOWN for the next scheduled pulse", 1,
            next.count { it.type == 3 && it.code == ABS_MT_TRACKING_ID && it.value >= 0 })
        assertEquals(2, frameGroups(next).size)
        receiver.accept(next)
        assertEquals(19455, receiver.contacts.getValue(11)[ABS_MT_POSITION_X])
    }

}

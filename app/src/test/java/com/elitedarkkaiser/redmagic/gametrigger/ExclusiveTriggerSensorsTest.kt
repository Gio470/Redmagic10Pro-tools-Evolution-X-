package com.elitedarkkaiser.redmagic.gametrigger

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ExclusiveTriggerSensorsTest {
    private class FakeInput(private val failGrab: Boolean = false) : ExclusiveTriggerSensors.Input {
        @Volatile var grabbed = false
        @Volatile var closed = false
        var androidKeys = 0
        val events = LinkedBlockingQueue<ExclusiveTriggerSensors.Key>()

        override fun grab() {
            if (failGrab) error("grab denied")
            grabbed = true
        }
        fun emit(code: Int, value: Int) {
            if (!grabbed) androidKeys++
            events.offer(ExclusiveTriggerSensors.Key(code, value))
        }
        override fun poll(): List<ExclusiveTriggerSensors.Key> {
            val key = events.poll(10, TimeUnit.MILLISECONDS)
            return listOfNotNull(key)
        }
        override fun close() { grabbed = false; closed = true }
    }

    @Test fun mappedF7AndF8ReachTheMapperWithoutAlsoReachingAndroid() {
        val inputs = listOf(FakeInput(), FakeInput())
        val seen = LinkedBlockingQueue<String>()
        val sensors = ExclusiveTriggerSensors(listOf("left", "right"),
            { side ->
                assertTrue("both sensors must already be grabbed", inputs.all { it.grabbed })
                seen.add("down:$side")
            }, { side -> seen.add("up:$side") }, { seen.add("failed:$it") },
            { path -> inputs[if (path == "left") 0 else 1] })
        try {
            sensors.start()
            inputs[0].emit(0x41, 1) // KEY_F7
            inputs[1].emit(0x42, 1) // KEY_F8 from the supplied NX789J capture
            val downs = setOf(seen.poll(2, TimeUnit.SECONDS), seen.poll(2, TimeUnit.SECONDS))
            assertEquals(setOf("down:0", "down:1"), downs)
            assertEquals(0, inputs.sumOf { it.androidKeys })
            inputs[0].emit(0x41, 0)
            inputs[1].emit(0x42, 0)
            assertEquals(setOf("up:0", "up:1"),
                setOf(seen.poll(2, TimeUnit.SECONDS), seen.poll(2, TimeUnit.SECONDS)))
            assertEquals(2L, sensors.presses)
        } finally { sensors.stop() }
        assertFalse(sensors.active)
        assertTrue(inputs.all { it.closed && !it.grabbed })
        inputs[1].emit(0x42, 1)
        assertEquals("outside mapping, quick actions/Android can receive keys again", 1, inputs[1].androidKeys)
    }

    @Test fun failureOnSecondGrabClosesBothDescriptorsAndDoesNotMapAnyKey() {
        val left = FakeInput()
        val right = FakeInput(failGrab = true)
        val sensors = ExclusiveTriggerSensors(listOf("left", "right"), { error("unexpected press") },
            { error("unexpected release") }, {}, { if (it == "left") left else right })
        val failure = runCatching { sensors.start() }.exceptionOrNull()
        assertEquals("grab denied", failure?.message)
        assertFalse(sensors.active)
        assertTrue(left.closed && right.closed)
        assertFalse(left.grabbed || right.grabbed)
    }

    @Test fun multipleSensorKeysAndAutorepeatStillFormOnePhysicalHold() {
        val input = FakeInput()
        val seen = LinkedBlockingQueue<String>()
        val sensors = ExclusiveTriggerSensors(listOf("right"), { seen.add("down") },
            { seen.add("up") }, { seen.add("failed:$it") }, { input })
        try {
            sensors.start()
            input.emit(0x42, 1)
            input.emit(0x3b, 1) // Some firmware reports a second key on the same sensor.
            input.emit(0x42, 2)
            input.emit(0x3b, 0)
            input.emit(0x42, 0)
            assertEquals("down", seen.poll(2, TimeUnit.SECONDS))
            assertEquals("up", seen.poll(2, TimeUnit.SECONDS))
            assertNull(seen.poll(50, TimeUnit.MILLISECONDS))
            assertEquals(1L, sensors.presses)
        } finally { sensors.stop() }
    }

    @Test fun aReadFailureHandsTheKeysBackBeforeReportingFailure() {
        val failed = CountDownLatch(1)
        var closed = false
        var failureMessage: String? = null
        val input = object : ExclusiveTriggerSensors.Input {
            override fun grab() {}
            override fun poll(): List<ExclusiveTriggerSensors.Key> = error("sensor disconnected")
            override fun close() { closed = true }
        }
        lateinit var sensors: ExclusiveTriggerSensors
        sensors = ExclusiveTriggerSensors(listOf("right"), {}, {}, {
            if (closed && !sensors.active) failureMessage = it.message
            failed.countDown()
        }, { input })
        try {
            sensors.start()
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            assertTrue(closed)
            assertEquals("sensor disconnected", failureMessage)
            assertFalse(sensors.active)
        } finally { sensors.stop() }
    }
}

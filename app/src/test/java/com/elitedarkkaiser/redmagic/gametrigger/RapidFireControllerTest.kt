package com.elitedarkkaiser.redmagic.gametrigger

import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue

class RapidFireControllerTest {
    /** A controllable clock, including tasks which were already dequeued when cancelled. */
    private class Clock : RapidFireController.Scheduler {
        data class Job(val time: Long, val serial: Int, val action: () -> Unit, var cancelled: Boolean = false)
        private val jobs = PriorityQueue<Job>(compareBy<Job> { it.time }.thenBy { it.serial })
        val all = mutableListOf<Job>()
        var now = 0L
        override fun schedule(delayMs: Long, task: () -> Unit): RapidFireController.Cancellation {
            val job = Job(now + delayMs, all.size, task)
            jobs += job
            all += job
            return RapidFireController.Cancellation { job.cancelled = true }
        }
        fun advance(ms: Long) {
            val end = now + ms
            while (jobs.isNotEmpty() && jobs.peek().time <= end) {
                val job = jobs.remove()
                now = job.time
                if (!job.cancelled) job.action()
            }
            now = end
        }
    }

    @Test fun releasingDuringATapLiftsImmediatelyAndCannotEmitAnotherTap() {
        val clock = Clock()
        val events = mutableListOf<String>()
        val controller = RapidFireController(clock)
        controller.start(0, 100, { events += if (it) "down" else "up" }, { events += "stop" })
        clock.advance(10)
        controller.stop(0)
        assertEquals(listOf("down", "up", "stop"), events)
        clock.all.toList().forEach { it.action() } // Simulate callbacks which escaped future.cancel.
        clock.advance(10000)
        assertEquals(listOf("down", "up", "stop"), events)
    }

    @Test fun releasingDuringTheGapCancelsTheNextDownAndEndsAimOwnership() {
        val clock = Clock()
        val events = mutableListOf<String>()
        val controller = RapidFireController(clock)
        controller.start(1, 100, { events += if (it) "down" else "up" }, { events += "stop" })
        clock.advance(40)
        assertEquals(listOf("down", "up"), events)
        controller.stop(1)
        clock.all.toList().forEach { it.action() }
        clock.advance(10000)
        assertEquals(listOf("down", "up", "stop"), events)
    }

    @Test fun aStaleUpFromThePreviousPressCannotLiftANewPress() {
        val clock = Clock()
        val events = mutableListOf<String>()
        val controller = RapidFireController(clock)
        controller.start(0, 5, { events += "old:$it" })
        val oldUp = clock.all.single()
        controller.stop(0)
        controller.start(0, 5, { events += "new:$it" })
        oldUp.action()
        assertEquals(listOf("old:true", "old:false", "new:true"), events)
        clock.advance(40)
        assertEquals("new:false", events.last())
        controller.stopAll()
    }

    @Test fun cancellingOneShoulderDoesNotStopTheOtherShoulder() {
        val clock = Clock()
        val left = mutableListOf<Boolean>()
        val right = mutableListOf<Boolean>()
        val controller = RapidFireController(clock)
        controller.start(0, 3, { left += it })
        controller.start(1, 3, { right += it })
        controller.stop(0)
        clock.advance(80)
        assertEquals(listOf(true, false), left)
        assertEquals(listOf(true, false, true), right)
        controller.stopAll()
    }

    @Test fun theConfiguredTapLimitFinishesWithNoContactOrQueuedRepeat() {
        val clock = Clock()
        val events = mutableListOf<Boolean>()
        var stops = 0
        val controller = RapidFireController(clock)
        controller.start(0, 3, { events += it }, { stops++ })
        clock.advance(10000)
        assertEquals(listOf(true, false, true, false, true, false), events)
        assertEquals(1, stops)
        controller.stop(0)
        assertEquals(1, stops)
    }

    @Test fun replacingTheMappingCancelsBothShouldersAndIgnoresTheirOldTasks() {
        val clock = Clock()
        val events = mutableListOf<String>()
        val controller = RapidFireController(clock)
        controller.start(0, 100, { events += "oldL:$it" })
        controller.start(1, 100, { events += "oldR:$it" })
        val stale = clock.all.toList()
        controller.stopAll()
        controller.start(1, 1, { events += "newR:$it" })
        stale.forEach { it.action() }
        assertEquals("newR:true", events.last())
        clock.advance(10000)
        assertEquals("newR:false", events.last())
        assertEquals(2, events.count { it.endsWith("true") && it.startsWith("old") })
    }

    @Test fun cancellationStillStopsBothSidesWhenOneReleaseCallbackFails() {
        val clock = Clock()
        var rightReleased = false
        val controller = RapidFireController(clock)
        controller.start(0, 5, { if (!it) error("left output failed") })
        controller.start(1, 5, { if (!it) rightReleased = true })
        assertEquals("left output failed", runCatching { controller.stopAll() }.exceptionOrNull()?.message)
        assertTrue(rightReleased)
        clock.advance(10000)
        // Both sessions have already been removed despite the output failure.
        controller.stopAll()
    }

    @Test fun aScheduledOutputFailureEndsTheHoldAndReportsTheError() {
        val clock = Clock()
        var stopped = false
        var failure: Throwable? = null
        val controller = RapidFireController(clock, onFailure = { failure = it })
        controller.start(0, 5, { if (!it) error("output failed") }, { stopped = true })
        clock.advance(40)
        assertTrue(stopped)
        assertEquals("output failed", failure?.message)
        clock.advance(10000)
    }

    @Test fun dragAimShortensThePauseWithoutChangingTheTapRateOrBurstLimit() {
        val clock = Clock()
        val ordinary = mutableListOf<Pair<Long, Boolean>>()
        val aiming = mutableListOf<Pair<Long, Boolean>>()
        val controller = RapidFireController(clock)
        controller.start(0, 3, { ordinary += clock.now to it })
        controller.start(1, 3, { aiming += clock.now to it }, timing = RapidFireController.SMOOTH_AIM)
        clock.advance(1000)
        assertEquals(listOf(0L, 80L, 160L), ordinary.filter { it.second }.map { it.first })
        assertEquals(ordinary.filter { it.second }, aiming.filter { it.second })
        assertEquals(listOf(40L, 120L, 200L), ordinary.filterNot { it.second }.map { it.first })
        assertEquals(listOf(72L, 152L, 232L), aiming.filterNot { it.second }.map { it.first })
    }

    @Test fun dragAimStillStopsImmediatelyDuringTheLongerDownOrShorterGap() {
        for (releaseAt in listOf(60L, 76L)) {
            val clock = Clock()
            val events = mutableListOf<Pair<Long, Boolean>>()
            var stopTime = -1L
            val controller = RapidFireController(clock)
            controller.start(1, 100, { events += clock.now to it }, { stopTime = clock.now },
                timing = RapidFireController.SMOOTH_AIM)
            clock.advance(releaseAt)
            controller.stop(1)
            val released = events.toList()
            assertEquals(releaseAt, stopTime)
            assertEquals(listOf(0L to true, minOf(releaseAt, 72L) to false), released)
            clock.all.toList().forEach { it.action() }
            clock.advance(10000)
            assertEquals(released, events)
        }
    }
}

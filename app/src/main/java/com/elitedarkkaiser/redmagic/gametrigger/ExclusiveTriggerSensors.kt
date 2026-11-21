package com.elitedarkkaiser.redmagic.gametrigger

import java.io.Closeable
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A sensor key must reach either Android or the mapper, never both at once. */
internal class ExclusiveTriggerSensors(
    private val paths: List<String>,
    private val onPress: (Int) -> Unit,
    private val onRelease: (Int) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val open: (String) -> Input = { EvdevTriggerInput(it) }
) {
    data class Key(val code: Int, val value: Int)
    interface Input : Closeable {
        fun grab()
        fun poll(): List<Key>
    }

    @Volatile var active = false
        private set
    @Volatile var presses = 0L
        private set
    private var reader: Thread? = null

    /** Acquire both sensors before dispatching any press. Roll back even a partial grab. */
    @Synchronized fun start() {
        if (active) return
        stop()
        check(reader == null) { "previous trigger reader has not stopped" }
        val inputs = mutableListOf<Input>()
        try {
            paths.forEach { path ->
                val input = open(path)
                inputs += input
                input.grab()
            }
        } catch (e: Throwable) {
            inputs.asReversed().forEach { runCatching { it.close() } }
            throw e
        }
        active = true
        reader = Thread({ readLoop(inputs) }, "exclusive-trigger-sensors").apply {
            isDaemon = true
            start()
        }
    }

    /** Closing the grabbing descriptors returns keys to Android and the quick-action service. */
    @Synchronized fun stop() {
        active = false
        reader?.join(500)
        if (reader?.isAlive != true) reader = null
    }

    private fun readLoop(inputs: List<Input>) {
        val held = Array(inputs.size) { mutableSetOf<Int>() }
        var failure: Throwable? = null
        try {
            while (active) {
                inputs.forEachIndexed { side, input ->
                    input.poll().forEach { key ->
                        if (!active) return@forEach
                        val wasDown = held[side].isNotEmpty()
                        when (key.value) {
                            1 -> held[side] += key.code
                            0 -> held[side] -= key.code
                            // Linux key repeat is not another physical press.
                            else -> return@forEach
                        }
                        val down = held[side].isNotEmpty()
                        if (down && !wasDown) { presses++; onPress(side) }
                        else if (!down && wasDown) onRelease(side)
                    }
                }
            }
        } catch (e: Throwable) {
            failure = e
        } finally {
            active = false
            inputs.asReversed().forEach { runCatching { it.close() } }
            // Report only once keys have actually been handed back.
            failure?.let(onFailure)
        }
    }
}

/** EVIOCGRAB's non-null argument claims the device; closing the descriptor releases it. */
internal object EvdevGrab {
    private const val EVIOCGRAB = 0x40044590

    fun take(fd: FileDescriptor) {
        val os = Class.forName("libcore.io.Libcore").getField("os").get(null)
        val withRef = os.javaClass.methods.firstOrNull {
            it.name == "ioctlInt" && it.parameterTypes.size == 3
        }
        if (withRef != null) {
            val ref = withRef.parameterTypes[2].getConstructor(Int::class.javaPrimitiveType).newInstance(1)
            withRef.invoke(os, fd, EVIOCGRAB, ref)
        } else {
            os.javaClass.methods.first { it.name == "ioctlInt" && it.parameterTypes.size == 2 }
                .invoke(os, fd, EVIOCGRAB)
        }
    }
}

private class EvdevTriggerInput(path: String) : ExclusiveTriggerSensors.Input {
    private val fd = android.system.Os.open(path,
        android.system.OsConstants.O_RDONLY or android.system.OsConstants.O_NONBLOCK, 0)
    private val poll = android.system.StructPollfd().apply {
        fd = this@EvdevTriggerInput.fd
        events = android.system.OsConstants.POLLIN.toShort()
    }
    private val buffer = ByteArray(24 * 32)

    override fun grab() = EvdevGrab.take(fd)

    override fun poll(): List<ExclusiveTriggerSensors.Key> {
        poll.revents = 0
        android.system.Os.poll(arrayOf(poll), 25)
        check(poll.revents.toInt() and (android.system.OsConstants.POLLERR or
            android.system.OsConstants.POLLHUP or android.system.OsConstants.POLLNVAL) == 0) {
            "trigger sensor disconnected"
        }
        if (poll.revents.toInt() and android.system.OsConstants.POLLIN == 0) return emptyList()
        val n = try { android.system.Os.read(fd, buffer, 0, buffer.size) }
        catch (e: android.system.ErrnoException) {
            if (e.errno == android.system.OsConstants.EAGAIN) 0 else throw e
        }
        val events = ByteBuffer.wrap(buffer, 0, n).order(ByteOrder.LITTLE_ENDIAN)
        return buildList {
            while (events.remaining() >= 24) {
                events.position(events.position() + 16)
                val type = events.short.toInt()
                val code = events.short.toInt()
                val value = events.int
                check(type != 0 || code != 3) { "trigger sensor input overflow" } // SYN_DROPPED
                if (type == 1) add(ExclusiveTriggerSensors.Key(code, value))
            }
        }
    }

    override fun close() = android.system.Os.close(fd)
}

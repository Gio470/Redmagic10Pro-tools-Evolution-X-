package com.elitedarkkaiser.redmagic.gametrigger

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One writer for whole physical frames and trigger changes, gated by the actual input grab. */
internal class TouchRelayState(
    private val abs: Map<Int, TouchInjectorMath.Axis>,
    private val fingerSlots: Int,
    private val out: OutputStream
) {
    data class Update(val slot: Int, val code: Int, val value: Int)

    private val width = TouchInjectorMath.MT_LAST - TouchInjectorMath.MT_FIRST + 1
    private val tracking = index(TouchInjectorMath.ABS_MT_TRACKING_ID)
    private val state = Array(fingerSlots + TouchInjectorMath.EXTRA_SLOTS) { IntArray(width) }
    private val sent = Array(state.size) { IntArray(width) }
    private val physical = Array(fingerSlots) { IntArray(width) }
    private val previousIds = IntArray(fingerSlots)
    private val frame = ByteBuffer.allocate(EVENT_SIZE * (state.size * width + 4)).order(ByteOrder.LITTLE_ENDIAN)
    private val originX = IntArray(fingerSlots)
    private val originY = IntArray(fingerSlots)
    private val born = LongArray(fingerSlots)
    private val touchStartX = IntArray(fingerSlots)
    private val touchStartY = IntArray(fingerSlots)
    private var sequence = 0L
    private data class DragAim(val side: Int, val rotation: Int, val width: Int, val height: Int)
    private var dragAim: DragAim? = null
    private var aimSlot = -1
    private var lastAimX = 0
    private var lastAimY = 0
    private var restartAim = false
    private val startX = IntArray(2)
    private val startY = IntArray(2)
    private val held = BooleanArray(2)
    private val rapidAim = BooleanArray(2)
    private var pendingAimX = 0
    private var pendingAimY = 0
    private var aimMoves = 0L
    private var aimResets = 0L
    private val idMax = abs.getValue(TouchInjectorMath.ABS_MT_TRACKING_ID).max.coerceAtLeast(16)
    private val axes = abs.keys.filter { it in TouchInjectorMath.MT_FIRST + 1..TouchInjectorMath.MT_LAST }.sorted().toIntArray()
    private var nextId = 0
    private var captured = false
    private var touching = false
    private var frames = 0L
    private var overlapFrames = 0L

    init {
        state.forEach { it[tracking] = -1 }
        sent.forEach { it[tracking] = -1 }
        physical.forEach { it[tracking] = -1 }
    }

    private fun index(code: Int) = code - TouchInjectorMath.MT_FIRST

    /** Pending trigger holds become visible only after all sources have been grabbed. */
    @Synchronized fun capture() {
        captured = true
        flush()
    }

    /** Accept only complete source frames. A trigger must never flush half a finger update. */
    @Synchronized fun sourceFrame(updates: List<Update>) {
        if (!captured) return
        for (s in physical.indices) previousIds[s] = physical[s][tracking]
        updates.forEach { (slot, code, value) ->
            require(slot in 0 until fingerSlots)
            val row = physical[slot]
            row[index(code)] = if (code == TouchInjectorMath.ABS_MT_TRACKING_ID && value >= 0) {
                if (row[tracking] >= 0) row[tracking] else {
                    val id = nextId
                    nextId = (nextId + 1) % (idMax - 2)
                    id
                }
            } else value
        }
        for (s in physical.indices) {
            if (physical[s][tracking] >= 0 && physical[s][tracking] != previousIds[s]) {
                resetOrigin(s)
                touchStartX[s] = originX[s]
                touchStartY[s] = originY[s]
                born[s] = ++sequence
            }
        }
        updateDragAim()
        rebuildFingers()
        frames++
        if ((0 until fingerSlots).any { physical[it][tracking] >= 0 } &&
            (fingerSlots until state.size).any { state[it][tracking] >= 0 }) overlapFrames++
        flush()
    }

    @Synchronized fun contact(side: Int, x: Int, y: Int, pressed: Boolean, keepAim: Boolean = false) {
        val row = state[fingerSlots + side]
        val wasHeld = held[side]
        held[side] = pressed || (keepAim && wasHeld)
        rapidAim[side] = held[side] && keepAim
        row[tracking] = if (pressed) idMax - side else -1
        if (pressed) {
            startX[side] = x
            startY[side] = y
            row[index(TouchInjectorMath.ABS_MT_POSITION_X)] = x
            row[index(TouchInjectorMath.ABS_MT_POSITION_Y)] = y
            abs[TouchInjectorMath.ABS_MT_PRESSURE]?.let {
                row[index(TouchInjectorMath.ABS_MT_PRESSURE)] = maxOf(1, it.max / 2)
            }
            for (code in listOf(TouchInjectorMath.ABS_MT_TOUCH_MAJOR, TouchInjectorMath.ABS_MT_WIDTH_MAJOR)) {
                abs[code]?.let { row[index(code)] = maxOf(1, it.max / 20) }
            }
        }
        if (dragAim?.side == side && wasHeld != held[side]) {
            aimSlot = -1
            restartAim = false
            pendingAimX = 0
            pendingAimY = 0
            physical.indices.forEach(::resetOrigin)
            rebuildFingers()
        }
        // While waiting, retain just the current holds. A completed tap must not fire later.
        if (pressed && dragAim?.side == side) restartAim = false
        if (captured) {
            // Every rapid-fire DOWN starts inside Shoot. Apply movement accumulated during
            // the UP gap only after that DOWN frame, without exposing a second aim pointer.
            flush()
            if (pressed && dragAim?.side == side && (pendingAimX != 0 || pendingAimY != 0)) {
                val dx = pendingAimX
                val dy = pendingAimY
                pendingAimX = 0
                pendingAimY = 0
                moveAim(side, dx, dy)
                flush()
            }
        }
    }

    @Synchronized fun release() {
        state.forEach { it[tracking] = -1 }
        physical.forEach { it[tracking] = -1 }
        held.fill(false)
        rapidAim.fill(false)
        aimSlot = -1
        restartAim = false
        pendingAimX = 0
        pendingAimY = 0
        try {
            if (captured) flush()
        } finally {
            captured = false
        }
    }

    @Synchronized fun diagnostics(): String {
        val fingers = physical.count { it[tracking] >= 0 }
        val triggers = (fingerSlots until state.size).count { state[it][tracking] >= 0 }
        val aim = dragAim?.let { "; aim=${if (it.side == 0) "L" else "R"}; aimMoves=$aimMoves; aimResets=$aimResets" }.orEmpty()
        return "frames=$frames; overlapFrames=$overlapFrames; fingers=$fingers; triggers=$triggers$aim"
    }

    /** One Shoot contact can own drag aiming. Other trigger behaviors retain their usual routing. */
    @Synchronized fun configureDragAim(side: Int?, rotation: Int, displayWidth: Int, displayHeight: Int) {
        require(side == null || side in 0..1)
        val next = side?.let { DragAim(it, rotation, displayWidth, displayHeight) }
        if (next == dragAim) return
        // A settings/rotation change ends the old held action before reassigning any finger.
        for (s in fingerSlots until state.size) state[s][tracking] = -1
        held.fill(false)
        rapidAim.fill(false)
        aimSlot = -1
        restartAim = false
        pendingAimX = 0
        pendingAimY = 0
        dragAim = next
        physical.indices.forEach(::resetOrigin)
        rebuildFingers()
        if (captured) flush()
    }

    private fun resetOrigin(slot: Int) {
        originX[slot] = physical[slot][index(TouchInjectorMath.ABS_MT_POSITION_X)]
        originY[slot] = physical[slot][index(TouchInjectorMath.ABS_MT_POSITION_Y)]
    }

    private fun rebuildFingers() {
        for (s in physical.indices) {
            physical[s].copyInto(state[s])
            if (s == aimSlot) state[s][tracking] = -1
        }
    }

    private fun updateDragAim() {
        val config = dragAim ?: return
        val trigger = state[fingerSlots + config.side]
        if (!held[config.side]) return
        val xi = index(TouchInjectorMath.ABS_MT_POSITION_X)
        val yi = index(TouchInjectorMath.ABS_MT_POSITION_Y)
        val xAxis = abs.getValue(TouchInjectorMath.ABS_MT_POSITION_X)
        val yAxis = abs.getValue(TouchInjectorMath.ABS_MT_POSITION_Y)
        if (aimSlot >= 0 && physical[aimSlot][tracking] < 0) {
            // Keep firing at the last point until another swipe starts. Never warp a held
            // contact back to Shoot: that would drag the camera in the opposite direction.
            aimSlot = -1
            restartAim = true
            physical.indices.forEach(::resetOrigin)
        }
        var dx: Int
        var dy: Int
        if (aimSlot < 0) {
            val rotated = config.rotation == 1 || config.rotation == 3
            val naturalWidth = if (rotated) config.height else config.width
            val naturalHeight = if (rotated) config.width else config.height
            fun isSwipe(s: Int): Boolean {
                if (physical[s][tracking] < 0) return false
                // The camera pad is the right half of the current display, not the raw panel.
                val right = when (config.rotation) {
                    1 -> touchStartY[s] > (yAxis.min.toLong() + yAxis.max) / 2
                    2 -> touchStartX[s] < (xAxis.min.toLong() + xAxis.max) / 2
                    3 -> touchStartY[s] < (yAxis.min.toLong() + yAxis.max) / 2
                    else -> touchStartX[s] > (xAxis.min.toLong() + xAxis.max) / 2
                }
                val px = (physical[s][xi] - originX[s]).toDouble() * naturalWidth / (xAxis.max - xAxis.min + 1)
                val py = (physical[s][yi] - originY[s]).toDouble() * naturalHeight / (yAxis.max - yAxis.min + 1)
                return right && px * px + py * py >= 8 * 8
            }
            aimSlot = physical.indices.filter(::isSwipe).maxByOrNull { born[it] } ?: return
            dx = physical[aimSlot][xi] - originX[aimSlot]
            dy = physical[aimSlot][yi] - originY[aimSlot]
            if (restartAim && trigger[tracking] >= 0 && !rapidAim[config.side]) restartShoot(config.side)
        } else {
            dx = physical[aimSlot][xi] - lastAimX
            dy = physical[aimSlot][yi] - lastAimY
        }
        lastAimX = physical[aimSlot][xi]
        lastAimY = physical[aimSlot][yi]
        if (dx == 0 && dy == 0) return
        if (trigger[tracking] < 0) {
            pendingAimX += dx
            pendingAimY += dy
        } else moveAim(config.side, dx, dy)
    }

    private fun moveAim(side: Int, dx: Int, dy: Int) {
        val trigger = state[fingerSlots + side]
        val xi = index(TouchInjectorMath.ABS_MT_POSITION_X)
        val yi = index(TouchInjectorMath.ABS_MT_POSITION_Y)
        val xAxis = abs.getValue(TouchInjectorMath.ABS_MT_POSITION_X)
        val yAxis = abs.getValue(TouchInjectorMath.ABS_MT_POSITION_Y)
        // A fire button near the edge leaves little room in that direction. Lift and press
        // Shoot again at the edge so long strokes can continue without a reverse camera move.
        // Rapid aim already resets on the next scheduled tap; do not add unscheduled taps.
        if (!rapidAim[side] && (trigger[xi].toLong() + dx !in xAxis.min.toLong()..xAxis.max.toLong() ||
            trigger[yi].toLong() + dy !in yAxis.min.toLong()..yAxis.max.toLong())) restartShoot(side)
        trigger[xi] = (trigger[xi].toLong() + dx).coerceIn(xAxis.min.toLong(), xAxis.max.toLong()).toInt()
        trigger[yi] = (trigger[yi].toLong() + dy).coerceIn(yAxis.min.toLong(), yAxis.max.toLong()).toInt()
        aimMoves++
    }

    private fun restartShoot(side: Int) {
        val trigger = state[fingerSlots + side]
        // End the aiming finger's separate pointer as part of the same transition.
        rebuildFingers()
        trigger[tracking] = -1
        flush()
        trigger[tracking] = idMax - side
        trigger[index(TouchInjectorMath.ABS_MT_POSITION_X)] = startX[side]
        trigger[index(TouchInjectorMath.ABS_MT_POSITION_Y)] = startY[side]
        flush()
        restartAim = false
        aimResets++
    }

    private fun flush() {
        frame.clear()
        fun event(type: Int, code: Int, value: Int) {
            frame.putLong(0).putLong(0)
            frame.putShort(type.toShort()).putShort(code.toShort()).putInt(value)
        }
        var any = false
        var changed = false
        for (s in state.indices) {
            val now = state[s]
            val was = sent[s]
            if (now[tracking] < 0 && was[tracking] < 0) continue
            var slotWritten = false
            val fresh = now[tracking] >= 0 && now[tracking] != was[tracking]
            for (code in axes) {
                val i = index(code)
                // New contacts include zero coordinates and unchanged size/pressure too.
                if (now[i] == was[i] && !fresh) continue
                if (now[tracking] < 0 && i != tracking) continue
                if (!slotWritten) { event(3, TouchInjectorMath.ABS_MT_SLOT, s); slotWritten = true }
                event(3, code, now[i])
                was[i] = now[i]
                changed = true
            }
            if (now[tracking] >= 0) any = true
        }
        if (any != touching) {
            event(1, 0x14a, if (any) 1 else 0) // BTN_TOUCH
            event(1, 0x145, if (any) 1 else 0) // BTN_TOOL_FINGER
            touching = any
            changed = true
        }
        if (!changed) return
        event(0, 0, 0) // SYN_REPORT
        out.write(frame.array(), 0, frame.position())
        out.flush()
    }

    private companion object {
        const val EVENT_SIZE = 24
    }
}

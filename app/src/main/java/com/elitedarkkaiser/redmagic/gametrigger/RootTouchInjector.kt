package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.util.Log
import android.view.Surface
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

/** Result of programming the trigger mapping. */
data class TriggerApplyResult(val success: Boolean, val message: String)

/** Game trigger mapping for custom ROMs, which have no RedMagicOS trigger service. */
object RootTouchInjector {
    private const val TAG = "RedmagicRootTouch"

    private var process: Process? = null
    private var input: OutputStreamWriter? = null
    @Volatile private var ready: CountDownLatch? = null
    @Volatile private var readyLine: String? = null
    @Volatile private var hapticsEnabled = false
    @Volatile var active = false
        private set
    @Volatile var lastStatus = "Root touch mode has not started"
        private set

    /** "merged" when fingers and triggers share one virtual touchscreen, "direct" otherwise. */
    val mode: String?
        get() = readyLine?.removePrefix("READY ")?.substringBefore(" ·")?.substringBefore(" (")
            ?.takeIf { it == "merged" || it == "direct" }

    /** What the helper reported when it started: its engine (and why, if direct) and the panel. */
    val helperLine: String?
        get() = readyLine?.removePrefix("READY ")?.removePrefix("ERROR ")

    /** Set by [GameTrigger] so a press can vibrate at the user's strength. */
    @Volatile var onPress: (() -> Unit)? = null

    @Synchronized
    fun apply(
        context: Context,
        left: IntArray,
        right: IntArray,
        leftBehavior: NativeTgkTriggerBehavior,
        rightBehavior: NativeTgkTriggerBehavior,
        leftCount: Int,
        rightCount: Int,
        rotation: Int,
        displayWidth: Int,
        displayHeight: Int,
        haptics: Boolean
    ): TriggerApplyResult {
        val started = ensureStarted(context)
        if (!started.first) {
            return TriggerApplyResult(false, started.second)
        }
        val dragCount = listOf(leftBehavior, rightBehavior).count { it.isDragAim }
        if (dragCount > 1) return TriggerApplyResult(false, "Choose Drag aim on only the Shoot trigger")
        if (dragCount > 0 && mode != "merged") return TriggerApplyResult(false,
            "Drag aim needs merged touch: ${started.second}")
        hapticsEnabled = haptics
        val (lx, ly) = center(left)
        val (rx, ry) = center(right)
        send("MAP $lx $ly ${leftBehavior.name} $leftCount $rx $ry ${rightBehavior.name} $rightCount " +
            "$rotation $displayWidth $displayHeight")
        active = true
        lastStatus = "Root touch mode active · ${started.second}"
        return TriggerApplyResult(true, "Root touch mapping enabled")
    }

    @Synchronized
    fun off() {
        if (process == null) return
        send("OFF")
        active = false
        lastStatus = "Root touch mode idle"
    }

    @Synchronized
    fun shutdown() {
        runCatching { send("QUIT") }
        runCatching { process?.destroy() }
        process = null
        input = null
        active = false
    }

    private fun center(rect: IntArray): Pair<Int, Int> =
        ((rect[0] + rect[2]) / 2) to ((rect[1] + rect[3]) / 2)

    private fun send(line: String) {
        val writer = input ?: return
        runCatching {
            writer.write(line + "\n")
            writer.flush()
        }.onFailure {
            Log.w(TAG, "Root touch helper stopped", it)
            process = null
            input = null
            active = false
        }
    }

    /** Returns whether the helper is running, and what it found or why it could not start. */
    private fun ensureStarted(context: Context): Pair<Boolean, String> {
        process?.let { running ->
            if (runCatching { running.exitValue() }.isFailure) return true to (readyLine ?: "running")
            process = null
            input = null
        }
        val apk = context.applicationInfo.sourceDir.replace("'", "'\\''")
        val latch = CountDownLatch(1)
        ready = latch
        readyLine = null
        val started = runCatching { Runtime.getRuntime().exec(arrayOf("su")) }.getOrElse {
            return false to "Root access is needed for root touch mode"
        }
        val writer = OutputStreamWriter(started.outputStream)
        writer.write("exec env CLASSPATH='$apk' /system/bin/app_process /system/bin " +
            "com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorCli\n")
        writer.flush()
        process = started
        input = writer
        Thread({
            runCatching {
                BufferedReader(InputStreamReader(started.inputStream)).forEachLine { line ->
                    when {
                        line.startsWith("READY ") || line.startsWith("ERROR ") -> {
                            readyLine = line
                            latch.countDown()
                        }
                        line.startsWith("STATUS ") -> {
                            readyLine = "READY " + line.removePrefix("STATUS ")
                            if (line.contains(" · failed:")) {
                                active = false
                                lastStatus = "Root touch relay failed · " + line.substringAfter(" · failed:")
                            }
                        }
                        line.startsWith("PRESS") -> if (hapticsEnabled) onPress?.invoke()
                    }
                    Log.d(TAG, line)
                }
            }
            latch.countDown()
        }, "root-touch-reader").apply { isDaemon = true }.start()
        Thread({
            runCatching { started.errorStream.bufferedReader().forEachLine { Log.w(TAG, it) } }
        }, "root-touch-errors").apply { isDaemon = true }.start()

        latch.await(5, TimeUnit.SECONDS)
        val line = readyLine
        return when {
            line == null -> {
                shutdown()
                false to "The root touch helper did not start. Check root access."
            }
            line.startsWith("ERROR ") -> {
                shutdown()
                false to line.removePrefix("ERROR ")
            }
            else -> true to line.removePrefix("READY ")
        }
    }

}

/** The pure parts of [TouchInjectorCli], kept apart so they can be tested without a device. */
object TouchInjectorMath {
    const val ABS_MT_SLOT = 0x2f
    const val ABS_MT_TOUCH_MAJOR = 0x30
    const val ABS_MT_WIDTH_MAJOR = 0x32
    const val ABS_MT_POSITION_X = 0x35
    const val ABS_MT_POSITION_Y = 0x36
    const val ABS_MT_TRACKING_ID = 0x39
    const val ABS_MT_PRESSURE = 0x3a

    const val MT_FIRST = 0x2f
    const val MT_LAST = 0x3d

    /** The merged engine's virtual touchscreen: its name and the ids its IDC file is found by. */
    const val MERGED_NAME = "Redmagic Tools merged touch"
    const val MERGED_VENDOR = 0x7262
    const val MERGED_PRODUCT = 0x6d63
    /** Trigger contacts get their own slots past the panel's: one each. */
    const val EXTRA_SLOTS = 2

    data class Axis(val min: Int, val max: Int)
    data class InputDevice(val path: String, val name: String, val abs: Map<Int, Axis>) {
        val isTouchscreen: Boolean
            get() = ABS_MT_POSITION_X in abs && ABS_MT_POSITION_Y in abs && ABS_MT_TRACKING_ID in abs &&
                ABS_MT_SLOT in abs && !name.contains("nubia_tgk", ignoreCase = true)
    }

    /** Parses `getevent -p`: each device, its name, and the range of every absolute axis. */
    fun parseDevices(output: String): List<InputDevice> {
        val devices = mutableListOf<InputDevice>()
        var path: String? = null
        var name = ""
        var abs = mutableMapOf<Int, Axis>()
        var inAbs = false
        fun flush() { path?.let { devices += InputDevice(it, name, abs) } }
        val axis = Regex("""([0-9a-fA-F]{4})\s*:\s*value\s+-?\d+,\s*min\s+(-?\d+),\s*max\s+(-?\d+)""")
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("add device") -> {
                    flush()
                    path = line.substringAfter(": ").trim()
                    name = ""
                    abs = mutableMapOf()
                    inAbs = false
                }
                line.startsWith("name:") -> name = line.substringAfter("name:").trim().trim('"')
                line.startsWith("ABS") -> inAbs = true
                Regex("""^[A-Z]{2,}\s*\(""").containsMatchIn(line) -> inAbs = false
            }
            if (inAbs) axis.find(line)?.let {
                abs[it.groupValues[1].toInt(16)] = Axis(it.groupValues[2].toInt(), it.groupValues[3].toInt())
            }
        }
        flush()
        return devices
    }

    /**
     * The `uinput` tool's register command for the merged touchscreen: the panel's multi-touch axes
     * with [EXTRA_SLOTS] more slots for the triggers, BTN_TOUCH and BTN_TOOL_FINGER, and
     * INPUT_PROP_DIRECT so Android takes it for a touchscreen.
     */
    fun registerJson(abs: Map<Int, Axis>,
        slots: Int = (abs[ABS_MT_SLOT]?.max ?: 9) + 1 + EXTRA_SLOTS): String {
        val axes = abs.filterKeys { it in MT_FIRST..MT_LAST }.toSortedMap()
        val info = axes.entries.joinToString(",") { (code, axis) ->
            val max = if (code == ABS_MT_SLOT) slots - 1 else axis.max
            """{"code":$code,"info":{"value":0,"minimum":${axis.min},"maximum":$max,"fuzz":0,"flat":0,"resolution":0}}"""
        }
        return """{"id":1,"command":"register","name":"$MERGED_NAME","vid":$MERGED_VENDOR,"pid":$MERGED_PRODUCT,""" +
            """"bus":"usb","configuration":[{"type":100,"data":[1,3]},{"type":101,"data":[330,325]},""" +
            """{"type":103,"data":[${axes.keys.joinToString(",")}]},{"type":110,"data":[1]}],""" +
            """"abs_info":[$info]}"""
    }

    /**
     * The merged touchscreen's IDC file: whatever the real panel's says, then the three lines that
     * make Android treat it as the phone's own screen rather than something plugged in.
     */
    fun idc(realIdc: String?): String = buildString {
        realIdc?.lineSequence()?.forEach { line ->
            val key = line.substringBefore('=').trim()
            if (key != "device.internal" && key != "touch.deviceType") appendLine(line)
        }
        appendLine("device.internal = 1")
        appendLine("touch.deviceType = touchScreen")
        appendLine("touch.orientationAware = 1")
    }

    /**
     * The touchscreens Android is actually reading fingers from, by name, out of `dumpsys input`'s
     * input reader section: every device there with a touch mapper. A phone can have more than one
     * -- this one has its panel and a "gdix_input_agent" -- and a touch from one cannot join a
     * gesture on another, so the merged engine has to take every one of them.
     */
    fun androidTouchDevices(dumpsys: String): Set<String> {
        val reader = dumpsys.indexOf("Input Reader State")
        if (reader < 0) return emptySet()
        val section = dumpsys.substring(reader)
        val header = Regex("""(?m)^\s*Device -?\d+: (.+)$""")
        val starts = header.findAll(section).toList()
        return starts.mapIndexedNotNull { i, match ->
            val end = starts.getOrNull(i + 1)?.range?.first ?: section.length
            val block = section.substring(match.range.last + 1, end)
            match.groupValues[1].trim().takeIf {
                (block.contains("Touch Input Mapper") || block.contains("TouchInputMapper")) &&
                    !Regex("""mode\s*(?:=|-)\s*DISABLED\b""").containsMatchIn(block) && it != MERGED_NAME
            }
        }.toSet()
    }

    /** Whether `dumpsys input` shows the merged device as an internal touchscreen. */
    fun mergedDeviceUsable(dumpsys: String): Boolean {
        // The reader's own list, not the event hub's, which says nothing of how it is used.
        val reader = dumpsys.indexOf("Input Reader State").coerceAtLeast(0)
        val start = dumpsys.indexOf(MERGED_NAME, reader)
        if (start < 0) return false
        val block = dumpsys.substring(start).let { rest ->
            val next = Regex("""\n\s*Device -?\d+:""").find(rest, 1)
            if (next != null) rest.substring(0, next.range.first) else rest.take(6000)
        }
        return !block.contains("IsExternal: true") && block.contains("Touch", ignoreCase = false) &&
            !block.contains("DISABLED")
    }

    /**
     * A point on the rotated display, in pixels, to the touchscreen's own units. The panel reports
     * in its natural orientation whatever way the phone is held, so the rotation is undone first.
     */
    fun toRaw(
        x: Int, y: Int, rotation: Int, displayWidth: Int, displayHeight: Int,
        xAxis: Axis, yAxis: Axis
    ): Pair<Int, Int> {
        val rotated = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val naturalWidth = if (rotated) displayHeight else displayWidth
        val naturalHeight = if (rotated) displayWidth else displayHeight
        val (nx, ny) = when (rotation) {
            Surface.ROTATION_90 -> (naturalWidth - 1 - y) to x
            Surface.ROTATION_180 -> (naturalWidth - 1 - x) to (naturalHeight - 1 - y)
            Surface.ROTATION_270 -> y to (naturalHeight - 1 - x)
            else -> x to y
        }
        fun scale(value: Int, size: Int, axis: Axis): Int {
            val span = axis.max - axis.min + 1
            return (axis.min + value.coerceIn(0, size - 1).toLong() * span / size).toInt()
                .coerceIn(axis.min, axis.max)
        }
        return scale(nx, naturalWidth, xAxis) to scale(ny, naturalHeight, yAxis)
    }
}

/** Root touch's merged engine: every finger and both triggers through one virtual touchscreen. */
internal class MergedTouch private constructor(
    private val sources: List<TouchInjectorMath.InputDevice>,
    /** The device whose units the virtual touchscreen uses; the others are scaled to it. */
    val primary: TouchInjectorMath.InputDevice,
    private val tool: Process,
    private val out: FileOutputStream
) {
    private val offsets: IntArray
    private val slotsReal: Int
    init {
        var next = 0
        offsets = IntArray(sources.size) { i ->
            val at = next
            next += sources[i].abs.getValue(TouchInjectorMath.ABS_MT_SLOT).max + 1
            at
        }
        slotsReal = next
    }
    private val relay = TouchRelayState(primary.abs, slotsReal, out)
    var onStatus: (String) -> Unit = {}
    @Volatile private var problem: String? = null

    val failed get() = problem != null

    @Volatile var grabbed = false
        private set
    @Volatile private var running = false
    private var reader: Thread? = null

    /** Starts taking the touchscreens, as soon as no finger is on any of them. Returns at once. */
    @Synchronized fun start() {
        if (running) return
        // Do not start a second reader if a previous one is still handing back its grabs.
        stop()
        check(reader == null) { "previous touch reader has not stopped" }
        problem = null
        running = true
        onStatus("waiting for fingers up")
        reader = Thread({ readLoop() }, "merged-touch").apply { isDaemon = true; start() }
    }

    /** Hands the touchscreens back to Android, lifting every virtual contact first. */
    @Synchronized fun stop() {
        running = false
        reader?.join(500)
        if (reader?.isAlive != true) reader = null
    }

    /** A trigger contact going down at ([x], [y]) in [primary]'s units, or lifting. */
    fun contact(side: Int, x: Int, y: Int, pressed: Boolean, keepAim: Boolean = false) {
        check(problem == null) { problem.orEmpty() }
        relay.contact(side, x, y, pressed, keepAim)
    }

    fun configureDragAim(side: Int?, rotation: Int, width: Int, height: Int) =
        relay.configureDragAim(side, rotation, width, height)

    /** Lifts everything on the virtual touchscreen. */
    fun releaseAll() {
        relay.release()
    }

    fun fail(error: Throwable) {
        problem = (error.cause?.message ?: error.message ?: error.javaClass.simpleName)
            .replace('\n', ' ').take(240)
        running = false
        onStatus("failed: $problem")
    }

    fun close() {
        stop()
        runCatching { releaseAll() }
        runCatching { out.close() }
        runCatching { tool.outputStream.close() }
        runCatching { tool.destroy() }
    }

    /** One source's parsing state. */
    private class Source(val device: TouchInjectorMath.InputDevice, val fd: java.io.FileDescriptor,
        val offset: Int) {
        var slot = 0
        val pending = mutableListOf<TouchRelayState.Update>()
        val seen = HashMap<Int, Boolean>()
        var lastEvent = android.os.SystemClock.uptimeMillis()
    }

    private fun readLoop() {
        val open = mutableListOf<Source>()
        try {
            sources.forEachIndexed { i, device ->
                val fd = android.system.Os.open(device.path, android.system.OsConstants.O_RDONLY or
                    android.system.OsConstants.O_NONBLOCK, 0)
                open += Source(device, fd, offsets[i])
            }
            val polls = open.map { source ->
                android.system.StructPollfd().apply {
                    fd = source.fd
                    events = android.system.OsConstants.POLLIN.toShort()
                }
            }.toTypedArray()
            val buffer = ByteArray(EVENT_SIZE * 64)
            var lastReport = 0L
            while (running) {
                polls.forEach { it.revents = 0 }
                android.system.Os.poll(polls, 50)
                open.forEachIndexed { i, source ->
                    check(polls[i].revents.toInt() and (android.system.OsConstants.POLLERR or
                        android.system.OsConstants.POLLHUP or android.system.OsConstants.POLLNVAL) == 0) {
                        "input device disconnected: ${source.device.path}"
                    }
                    if (polls[i].revents.toInt() and android.system.OsConstants.POLLIN == 0) return@forEachIndexed
                    val n = try { android.system.Os.read(source.fd, buffer, 0, buffer.size) }
                    catch (e: android.system.ErrnoException) {
                        if (e.errno == android.system.OsConstants.EAGAIN) 0 else throw e
                    }
                    if (n > 0) handle(source, ByteBuffer.wrap(buffer, 0, n).order(ByteOrder.LITTLE_ENDIAN))
                }
                if (!grabbed) {
                    // Before the grab: watch without taking anything until no source is touched.
                    val now = android.os.SystemClock.uptimeMillis()
                    val quiet = open.all { now - it.lastEvent >= QUIET_MS && it.seen.values.none { down -> down } }
                    if (quiet) {
                        open.forEach { EvdevGrab.take(it.fd) }
                        grabbed = true
                        relay.capture()
                        lastReport = 0L
                    }
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastReport >= 1000) {
                    onStatus(if (grabbed) "relaying · ${relay.diagnostics()}" else "waiting for fingers up")
                    lastReport = now
                }
            }
        } catch (e: Throwable) {
            fail(e)
        } finally {
            running = false
            runCatching { relay.release() }.onFailure { fail(it) }
            grabbed = false
            // Closing the descriptors is what ends the grabs.
            open.forEach { runCatching { android.system.Os.close(it.fd) } }
            if (problem == null) onStatus("idle · ${relay.diagnostics()}")
        }
    }

    private fun handle(source: Source, events: ByteBuffer) {
        val slots = source.device.abs.getValue(TouchInjectorMath.ABS_MT_SLOT).max + 1
        val xFrom = source.device.abs[TouchInjectorMath.ABS_MT_POSITION_X]
        val yFrom = source.device.abs[TouchInjectorMath.ABS_MT_POSITION_Y]
        val xTo = primary.abs[TouchInjectorMath.ABS_MT_POSITION_X]
        val yTo = primary.abs[TouchInjectorMath.ABS_MT_POSITION_Y]
        fun scale(value: Int, from: TouchInjectorMath.Axis?, to: TouchInjectorMath.Axis?): Int {
            if (from == null || to == null || from == to || from.max == from.min) return value
            return (to.min + (value - from.min).toLong() * (to.max - to.min) / (from.max - from.min)).toInt()
        }
        while (events.remaining() >= EVENT_SIZE) {
            events.position(events.position() + TYPE_OFFSET)
            val type = events.short.toInt()
            val code = events.short.toInt()
            val value = events.int
            source.lastEvent = android.os.SystemClock.uptimeMillis()
            if (!grabbed) {
                if (type == EV_ABS && code == TouchInjectorMath.ABS_MT_SLOT) source.slot = value
                if (type == EV_ABS && code == TouchInjectorMath.ABS_MT_TRACKING_ID) source.seen[source.slot] = value >= 0
                continue
            }
            when {
                // Resuming without EVIOCGMTSLOTS would leave lost fingers or triggers stuck down.
                type == EV_SYN && code == SYN_DROPPED -> error("touch input overflow: ${source.device.path}")
                type == EV_SYN && code == SYN_REPORT -> {
                    relay.sourceFrame(source.pending)
                    source.pending.clear()
                }
                type == EV_ABS && code == TouchInjectorMath.ABS_MT_SLOT -> source.slot = value
                type == EV_ABS && code in TouchInjectorMath.MT_FIRST + 1..TouchInjectorMath.MT_LAST &&
                    source.slot in 0 until slots -> {
                    val scaled = when (code) {
                        TouchInjectorMath.ABS_MT_POSITION_X -> scale(value, xFrom, xTo)
                        TouchInjectorMath.ABS_MT_POSITION_Y -> scale(value, yFrom, yTo)
                        else -> value
                    }
                    source.pending += TouchRelayState.Update(source.offset + source.slot, code, scaled)
                }
            }
        }
    }

    companion object {
        private const val EV_SYN = 0
        private const val EV_ABS = 3
        private const val SYN_REPORT = 0
        private const val SYN_DROPPED = 3
        private const val EVENT_SIZE = 24
        private const val TYPE_OFFSET = 16
        private const val QUIET_MS = 250L

        /**
         * Builds the virtual touchscreen over [sources] and checks Android has taken it as the
         * phone's own screen. Throws, with the reason, if any of that does not happen -- the
         * caller then keeps to the direct engine.
         */
        fun create(sources: List<TouchInjectorMath.InputDevice>): MergedTouch {
            check(sources.isNotEmpty()) { "no touchscreen to merge" }
            val uinput = File("/system/bin/uinput")
            check(uinput.exists()) { "no uinput tool on this ROM" }
            // The panel's units, not an agent's, if there is a choice.
            val primary = sources.firstOrNull { !it.name.contains("agent", ignoreCase = true) } ?: sources.first()
            val slots = sources.sumOf { it.abs.getValue(TouchInjectorMath.ABS_MT_SLOT).max + 1 } +
                TouchInjectorMath.EXTRA_SLOTS

            // The IDC file first: Android reads it when the device appears.
            val realIdc = listOf("/odm/usr/idc", "/vendor/usr/idc", "/product/usr/idc", "/system/usr/idc")
                .map { File(it, primary.name.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".idc") }
                .firstOrNull { it.isFile }
                ?.let { runCatching { it.readText() }.getOrNull() }
            val dir = File("/data/system/devices/idc").apply { mkdirs() }
            File(dir, "Vendor_%04x_Product_%04x.idc".format(
                TouchInjectorMath.MERGED_VENDOR, TouchInjectorMath.MERGED_PRODUCT
            )).writeText(TouchInjectorMath.idc(realIdc))
            runCatching {
                Runtime.getRuntime().exec(arrayOf("sh", "-c",
                    "chown -R system:system /data/system/devices; chmod 755 /data/system/devices " +
                        "/data/system/devices/idc; chmod 644 /data/system/devices/idc/*.idc; " +
                        "restorecon -R /data/system/devices")).waitFor()
            }

            val tool = ProcessBuilder(uinput.path, "-").redirectErrorStream(true).start()
            try {
                tool.outputStream.write((TouchInjectorMath.registerJson(primary.abs, slots) + "\n").toByteArray())
                tool.outputStream.flush()

                // The new event node, by name.
                var node: String? = null
                val deadline = android.os.SystemClock.uptimeMillis() + 3000
                while (node == null && android.os.SystemClock.uptimeMillis() < deadline) {
                    node = File("/sys/class/input").listFiles()?.firstOrNull { event ->
                        event.name.startsWith("event") && runCatching {
                            File(event, "device/name").readText().trim()
                        }.getOrNull() == TouchInjectorMath.MERGED_NAME
                    }?.let { "/dev/input/${it.name}" }
                    if (node == null) Thread.sleep(50)
                }
                checkNotNull(node) { "the virtual touchscreen did not appear" }

                // And Android using it as the phone's own touchscreen.
                var usable = false
                while (!usable && android.os.SystemClock.uptimeMillis() < deadline + 3000) {
                    usable = TouchInjectorMath.mergedDeviceUsable(dumpsysInput())
                    if (!usable) Thread.sleep(150)
                }
                check(usable) { "Android did not take the virtual touchscreen as the screen" }
                return MergedTouch(sources, primary, tool, FileOutputStream(node))
            } catch (e: Throwable) {
                runCatching { tool.destroy() }
                throw e
            }
        }

        fun dumpsysInput(): String = runCatching {
            Runtime.getRuntime().exec(arrayOf("dumpsys", "input")).inputStream.bufferedReader().readText()
        }.getOrDefault("")
    }
}

/**
 * The root helper. Runs as root through app_process, reads the trigger sensors and writes touch
 * frames: through [MergedTouch] where it can be built, otherwise straight into the touchscreen.
 * Commands arrive one per line on stdin:
 */
object TouchInjectorCli {
    private const val EV_SYN = 0
    private const val EV_KEY = 1
    private const val EV_ABS = 3
    private const val BTN_TOUCH = 0x14a
    private const val LEFT_SENSOR = "nubia_tgk_aw_sar0_ch0"
    private const val RIGHT_SENSOR = "nubia_tgk_aw_sar1_ch0"

    private class Target(val x: Int, val y: Int, val behavior: NativeTgkTriggerBehavior, val count: Int)
    private class Mapping(val left: Target, val right: Target, val rotation: Int, val width: Int, val height: Int)

    @Volatile private var mapping: Mapping? = null
    private val actionLock = Any()
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "root-touch-taps").apply { isDaemon = true }
    }
    private val rapid = RapidFireController(RapidFireController.Scheduler { delayMs, task ->
        val future = timer.schedule({ task() }, delayMs, TimeUnit.MILLISECONDS)
        RapidFireController.Cancellation { future.cancel(false) }
    }, actionLock, ::reportFailure)
    private lateinit var device: TouchInjectorMath.InputDevice
    private lateinit var touch: FileOutputStream
    private var merged: MergedTouch? = null
    private var mergeProblem: String? = null
    private var mergedNames: List<String> = emptyList()
    private lateinit var sensors: ExclusiveTriggerSensors
    @Volatile private var relayStatus = "idle"
    private val down = BooleanArray(2)
    private val trackingIds = intArrayOf(0x7f00, 0x7f01)

    @JvmStatic fun main(args: Array<String>) {
        val result = runCatching { setUp() }
        val error = result.exceptionOrNull()
        if (error != null) {
            println("ERROR ${error.message ?: error.javaClass.simpleName}")
            System.out.flush()
            kotlin.system.exitProcess(0)
        }
        println("READY ${helperDescription()}")
        System.out.flush()

        val stdin = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = stdin.readLine() ?: break
            val parts = line.trim().split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "MAP" -> runCatching {
                    val next = parseMap(parts)
                    synchronized(actionLock) {
                        stopActions()
                        val aims = listOf(next.left, next.right).indices.filter {
                            (if (it == 0) next.left else next.right).behavior.isDragAim
                        }
                        check(aims.size <= 1) { "Choose Drag aim on only the Shoot trigger" }
                        check(aims.isEmpty() || merged != null) { "Drag aim needs merged touch" }
                        merged?.configureDragAim(aims.singleOrNull(), next.rotation, next.width, next.height)
                        mapping = next
                    }
                    sensors.start()
                    merged?.start()
                    if (merged == null) relayStatus = "active"
                    reportStatus()
                }.onFailure { error ->
                    stopActions()
                    sensors.stop()
                    merged?.stop()
                    reportFailure(error)
                }
                "OFF" -> {
                    stopActions()
                    sensors.stop()
                    merged?.stop()
                    if (merged == null) relayStatus = "idle"
                    reportStatus()
                }
                "QUIT" -> break
            }
        }
        stopActions()
        sensors.stop()
        merged?.close()
        timer.shutdownNow()
        kotlin.system.exitProcess(0)
    }

    private fun setUp() {
        val listing = Runtime.getRuntime().exec(arrayOf("getevent", "-p")).inputStream.bufferedReader().readText()
        val touchscreens = TouchInjectorMath.parseDevices(listing).filter {
            it.isTouchscreen && it.name != TouchInjectorMath.MERGED_NAME
        }
        device = touchscreens.firstOrNull() ?: error("No multi-touch touchscreen found")
        check(File(sensorPath(LEFT_SENSOR)).exists() && File(sensorPath(RIGHT_SENSOR)).exists()) {
            "Shoulder trigger sensors not found"
        }
        sensors = ExclusiveTriggerSensors(listOf(sensorPath(LEFT_SENSOR), sensorPath(RIGHT_SENSOR)),
            ::onPress, ::onRelease, { error ->
                reportFailure(error)
            })
        touch = FileOutputStream(device.path)
        // Every touchscreen Android reads fingers from, so the triggers can join any of them.
        val used = TouchInjectorMath.androidTouchDevices(MergedTouch.dumpsysInput())
        val sources = touchscreens.filter { it.name in used }.ifEmpty { touchscreens }
        merged = runCatching { MergedTouch.create(sources) }
            .onFailure { mergeProblem = it.message ?: it.javaClass.simpleName }
            .getOrNull()
        mergedNames = sources.map { "${it.name} (${it.path})" }
        merged?.onStatus = { status ->
            relayStatus = status
            if (status.startsWith("failed:")) stopActions()
            reportStatus()
        }
    }
    private fun sensorPath(name: String): String {
        File("/sys/class/input").listFiles()?.filter { it.name.startsWith("event") }?.forEach { event ->
            val found = runCatching { File(event, "device/name").readText().trim() }.getOrNull()
            if (found == name) return "/dev/input/${event.name}"
        }
        return "/dev/input/missing-$name"
    }

    private fun parseMap(parts: List<String>): Mapping {
        fun target(offset: Int) = Target(
            parts[offset].toInt(), parts[offset + 1].toInt(),
            NativeTgkTriggerBehavior.valueOf(parts[offset + 2]), parts[offset + 3].toInt()
        )
        return Mapping(target(1), target(5), parts[9].toInt(), parts[10].toInt(), parts[11].toInt())
    }

    private fun helperDescription(): String {
        val engine = if (merged != null) "merged · ${mergedNames.joinToString(" + ")}" else
            "direct (${mergeProblem ?: "merge unavailable"}) · ${device.name} (${device.path})"
        return "$engine · $relayStatus · keys=${if (sensors.active) "exclusive" else "released"}; keyPresses=${sensors.presses}"
    }

    private fun reportStatus() {
        println("STATUS ${helperDescription()}")
        System.out.flush()
    }

    private fun stopActions() = synchronized(actionLock) {
        mapping = null
        try { rapid.stopAll() } finally { releaseAll() }
    }

    private fun reportFailure(error: Throwable) {
        runCatching { stopActions() }.onFailure { if (it !== error) error.addSuppressed(it) }
        val engine = merged
        if (engine != null) engine.fail(error)
        else {
            relayStatus = "failed: " + (error.cause?.message ?: error.message ?: error.javaClass.simpleName)
                .replace('\n', ' ').take(240)
            reportStatus()
        }
    }

    private fun onPress(side: Int) = synchronized(actionLock) {
        val map = mapping ?: return@synchronized
        val target = if (side == 0) map.left else map.right
        println(if (side == 0) "PRESS L" else "PRESS R")
        System.out.flush()
        when (target.behavior) {
            NativeTgkTriggerBehavior.LONG_PRESS,
            NativeTgkTriggerBehavior.DRAG_AIM -> {
                rapid.stop(side)
                contact(side, map, target, true)
            }
            NativeTgkTriggerBehavior.SINGLE_TOUCH,
            NativeTgkTriggerBehavior.RAPID_FIRE,
            NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE -> rapid.start(side,
                if (target.behavior.isRapidFire) target.count.coerceIn(1, 100) else 1,
                pulse = { pressed -> contact(side, map, target, pressed,
                    keepAim = target.behavior == NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE) },
                stopped = { contact(side, map, target, false, force = true) },
                timing = if (target.behavior == NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE)
                    RapidFireController.SMOOTH_AIM else RapidFireController.STANDARD)
        }
    }

    private fun onRelease(side: Int) = synchronized(actionLock) {
        val map = mapping ?: return@synchronized
        val target = if (side == 0) map.left else map.right
        if (target.behavior.isRapidFire) rapid.stop(side)
        else if (target.behavior != NativeTgkTriggerBehavior.SINGLE_TOUCH)
            contact(side, map, target, false, force = true)
    }

    /** One complete touch frame: our slot goes down at [target], or lifts. */
    private fun contact(side: Int, map: Mapping, target: Target, pressed: Boolean,
        keepAim: Boolean = false, force: Boolean = false): Unit = synchronized(actionLock) {
        if (pressed && mapping !== map) return@synchronized
        if (down[side] == pressed && !force) return@synchronized
        val abs = device.abs
        val engine = merged
        if (engine != null) {
            if (engine.failed) { down[side] = false; return@synchronized }
            // A created virtual device is not yet a grabbed source. Queue holds in the relay
            // until capture; never start a gesture on the original device while waiting.
            val units = engine.primary.abs
            val (x, y) = TouchInjectorMath.toRaw(target.x, target.y, map.rotation, map.width, map.height,
                units.getValue(TouchInjectorMath.ABS_MT_POSITION_X), units.getValue(TouchInjectorMath.ABS_MT_POSITION_Y))
            val result = runCatching { engine.contact(side, x, y, pressed, keepAim) }
            if (result.isSuccess) { down[side] = pressed; return@synchronized }
            // Hand the source back on failure, and report it. Switching to direct injection
            // here would silently put fingers and triggers on different devices again.
            down[side] = false
            engine.fail(result.exceptionOrNull() ?: IllegalStateException("the virtual touchscreen stopped"))
            return@synchronized
        }
        val slotAxis = abs.getValue(TouchInjectorMath.ABS_MT_SLOT)
        // The two highest slots: the panel hands fingers the lowest free ones first.
        val slot = slotAxis.max - side
        val frame = ByteBuffer.allocate(EVENT_SIZE * 10).order(ByteOrder.LITTLE_ENDIAN)
        fun event(type: Int, code: Int, value: Int) {
            frame.putLong(0).putLong(0)
            frame.putShort(type.toShort()).putShort(code.toShort()).putInt(value)
        }
        event(EV_ABS, TouchInjectorMath.ABS_MT_SLOT, slot)
        if (pressed) {
            val (x, y) = TouchInjectorMath.toRaw(target.x, target.y, map.rotation, map.width, map.height,
                abs.getValue(TouchInjectorMath.ABS_MT_POSITION_X), abs.getValue(TouchInjectorMath.ABS_MT_POSITION_Y))
            event(EV_ABS, TouchInjectorMath.ABS_MT_TRACKING_ID, trackingIds[side])
            event(EV_ABS, TouchInjectorMath.ABS_MT_POSITION_X, x)
            event(EV_ABS, TouchInjectorMath.ABS_MT_POSITION_Y, y)
            // Android treats a contact with zero pressure as hovering, so give it some.
            abs[TouchInjectorMath.ABS_MT_PRESSURE]?.let { event(EV_ABS, TouchInjectorMath.ABS_MT_PRESSURE, maxOf(1, it.max / 2)) }
            abs[TouchInjectorMath.ABS_MT_TOUCH_MAJOR]?.let { event(EV_ABS, TouchInjectorMath.ABS_MT_TOUCH_MAJOR, maxOf(1, it.max / 20)) }
            abs[TouchInjectorMath.ABS_MT_WIDTH_MAJOR]?.let { event(EV_ABS, TouchInjectorMath.ABS_MT_WIDTH_MAJOR, maxOf(1, it.max / 20)) }
            event(EV_KEY, BTN_TOUCH, 1)
        } else {
            event(EV_ABS, TouchInjectorMath.ABS_MT_TRACKING_ID, -1)
        }
        event(EV_SYN, 0, 0)
        // One write per frame, so the kernel takes it as a unit.
        touch.write(frame.array(), 0, frame.position())
        touch.flush()
        down[side] = pressed
    }

    private fun releaseAll() {
        val map = mapping ?: Mapping(Target(0, 0, NativeTgkTriggerBehavior.LONG_PRESS, 0),
            Target(0, 0, NativeTgkTriggerBehavior.LONG_PRESS, 0), 0, 2, 2)
        runCatching { contact(0, map, map.left, false, force = true) }
        runCatching { contact(1, map, map.right, false, force = true) }
    }

    /** struct input_event on 64-bit: a 16-byte timeval, then type, code and value. */
    private const val EVENT_SIZE = 24
}


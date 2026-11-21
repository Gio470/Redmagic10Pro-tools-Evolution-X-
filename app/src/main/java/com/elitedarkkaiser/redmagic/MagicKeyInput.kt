package com.elitedarkkaiser.redmagic

internal enum class MagicKeyMode(val value: Int, val label: String) {
    DISABLED(0, "Disabled"), CAMERA(1, "Camera"), GAME_SPACE(2, "GameSpace"),
    SOUND(3, "Sound Mode"), FLASHLIGHT(4, "Flashlight"), RECORDER(5, "Voice Recorder"), APP(16, "Launch App"), PERFORMANCE(17, "Performance mode");
    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: DISABLED
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: DISABLED
    }
}

internal data class MagicKeyConfig(val mode: MagicKeyMode, val app: String?, val reversed: Boolean)

/** The NX789J reports its slider as SW_PEN_INSERTED on gpio-keys_nubia. Never pick a touchscreen. */
internal object MagicKeyInput {
    data class Device(val path: String, val name: String, val initial: Int)
    private val header = Regex("""add device \d+: (/dev/input/event\d+)""")
    private val event = Regex("""(?:^|\s)(?:EV_SW\s+SW_PEN_INSERTED|0005\s+000f)\s+([0-9a-fA-F]{1,8})(?:\s|$)""")

    fun device(dump: String): Device? {
        val candidates = mutableListOf<Device>()
        var path = ""
        var name = ""
        var switches = false
        var value: Int? = null
        fun finish() {
            val known = name.equals("gpio-keys_nubia", true) ||
                (name.contains("nubia", true) && (name.contains("slider", true) || name.contains("magic", true)))
            if (known && value != null && path.matches(Regex("/dev/input/event\\d+")))
                candidates += Device(path, name, value!!)
        }
        dump.lineSequence().forEach { line ->
            val found = header.find(line)
            if (found != null) {
                finish(); path = found.groupValues[1]; name = ""; value = null; switches = false
            } else if (line.trimStart().startsWith("name:")) {
                name = line.substringAfter('"').substringBeforeLast('"')
            } else {
                val start = Regex("""\bSW\s+\(0005\):""").containsMatchIn(line)
                if (start) switches = true
                else if (line.contains(Regex("""\(\w{4}\):""")) || line.trimStart().startsWith("input props:")) switches = false
                if (switches) {
                    val codes = if (start) line.substringAfter(':') else line
                    Regex("""(?:^|\s)000f(\*)?(?:\s|$)""").find(codes)?.let {
                        value = if (it.groupValues[1] == "*") 1 else 0
                    }
                }
            }
        }
        finish()
        return candidates.firstOrNull { it.name.equals("gpio-keys_nubia", true) } ?: candidates.firstOrNull()
    }

    fun value(line: String): Int? = event.find(line)?.groupValues?.get(1)?.toIntOrNull(16)?.takeIf { it in 0..1 }
    class Edges(initial: Int) {
        private var last = initial
        /** Seeded from current state: startup and duplicate reports must never launch anything. */
        fun update(value: Int, reversed: Boolean): Boolean? {
            if (value !in 0..1 || value == last) return null
            last = value
            return (value == 1) != reversed
        }
    }
}

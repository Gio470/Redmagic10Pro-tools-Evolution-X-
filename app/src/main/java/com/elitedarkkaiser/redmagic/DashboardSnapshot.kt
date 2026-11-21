package com.elitedarkkaiser.redmagic

object DashboardSnapshot {

    private fun read(path: String): String? {
        return RootShell.execForOutput("cat $path")?.trim()?.ifEmpty { null }
    }

    fun readCpuTempC(): String {
        val candidates = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/class/thermal/thermal_zone65/temp",
            "/sys/class/thermal/thermal_zone80/temp"
        )

        for (path in candidates) {
            val raw = read(path)?.toFloatOrNull() ?: continue
            val c = if (raw > 1000f) raw / 1000f else raw
            if (c in 10f..120f) return String.format("%.1f", c)
        }
        return "?"
    }

    fun readCpuTempF(): String {
        val c = readCpuTempC().toFloatOrNull() ?: return "?"
        return String.format("%.1f", (c * 9f / 5f) + 32f)
    }

}

package com.elitedarkkaiser.redmagic.parts

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

/** Root helper: stdin is its lifetime lease. EOF restores only changes owned by this app. */
object DevicePartsCli {
    @JvmStatic fun main(args: Array<String>) {
        try {
            check(args.size == 2)
            check(android.os.Build.MODEL.equals("NX789J", true) || android.os.Build.DEVICE.equals("NX789J", true)) { "RedMagic 10 Pro (NX789J) required" }
            System.load(File(args[0], "libdeviceparts.so").path)
            Looper.prepareMainLooper()
            val handler = Handler(Looper.getMainLooper())
            val leaseFolder = File("/data/vendor/rmcc_parts").apply { mkdirs() }
            val lease = java.io.RandomAccessFile(File(leaseFolder, "owner.lock"), "rw")
            val ownership = lease.channel.lock()
            val hardware = PartsHardware(File(args[1]))
            val ambient = PartsAmbient(handler)
            hardware.recover()
            println("READY"); System.out.flush()
            var lastConfig: JSONObject? = null
            var hot = false
            fun applyAndReport(config: JSONObject) {
                val result = JSONObject()
                val errors = mutableListOf<String>()
                val temperature = listOf("/sys/class/power_supply/battery/temp", "/sys/class/qcom-battery/temp")
                    .firstNotNullOfOrNull { path -> runCatching { File(path).readText().trim().toDouble().let { if (kotlin.math.abs(it) >= 1000) (it / 100).toInt() else it.toInt() } }.getOrNull() }
                    ?: config.optInt("batteryTemp", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                hot = temperature == null || temperature >= if (hot) 480 else 550
                val requested = PerformanceProfile.parse(config.optString("profile"))
                var profile = if (hot) PerformanceProfile.BALANCED else requested
                val screen = runCatching { PartsContext.system.getSystemService(android.os.PowerManager::class.java).isInteractive }
                    .getOrDefault(config.optBoolean("screen"))
                val fanLevel = runCatching {
                    if (File("/sys/kernel/fan/fan_enable").readText().trim() == "1") File("/sys/kernel/fan/fan_speed_level").readText().trim().toInt() else 0
                }.getOrDefault(0)
                val neededFan = if (profile in listOf(PerformanceProfile.EXTREME, PerformanceProfile.ULTRA)) 5 else 1
                if (screen && profile != PerformanceProfile.BALANCED && fanLevel < neededFan) {
                    profile = PerformanceProfile.BALANCED; errors += "Performance is paused until the cooling fan is ready"
                }
                runCatching { hardware.performance(profile, screen) }
                    .onFailure { errors += it.message ?: "Performance mode failed" }
                val capacity = runCatching { File("/sys/class/power_supply/battery/capacity").readText().trim().toInt() }.getOrNull()
                    ?: config.optInt("percent", -1)
                runCatching { hardware.charge(config.optBoolean("separate") && capacity >= 20) }
                    .onFailure { errors += it.message ?: "Charge separation failed" }
                val aod = runCatching { PartsCommands.run("settings", "--user", "current", "get", "secure", "doze_always_on").trim() != "0" }
                    .getOrDefault(config.optBoolean("aod", true))
                ambient.configure(config.optBoolean("tap"), config.optBoolean("lift"), screen, aod)
                if (hot && requested != PerformanceProfile.BALANCED) errors += "Battery temperature guard is holding Balanced limits"
                result.put("fanLevel", fanLevel).put("profile", hardware.active.key).put("separated", hardware.separated)
                    .put("chargeDetail", hardware.chargeDetail)
                    .put("recovery", hardware.recoveryWarnings.joinToString(" · ").ifEmpty { "clean" })
                    .put("ambientDebug", ambient.debug).put("state", hardware.stateLocation)
                    .put("requested", "separate=${config.optBoolean("separate")} tap=${config.optBoolean("tap")} " +
                        "lift=${config.optBoolean("lift")} battery=$capacity%")
                    .put("ambient", ambient.detail).put("error", errors.joinToString(" · ")).put("aod", if (aod) 1 else 0)
                val games = runCatching { PartsPolicy.games(PartsCommands.run("settings", "--user", "current", "get", "system", "gamespace_game_list")) }.getOrDefault(emptySet())
                result.put("games", org.json.JSONArray(games.toList()))
                hardware.skinTemperature()?.let { result.put("skin", it) }
                println("STATUS $result"); System.out.flush()
            }
            Thread({
                try {
                    System.`in`.bufferedReader().forEachLine { line ->
                        if (line == "QUIT") { handler.post { Looper.myLooper()?.quitSafely() }; return@forEachLine }
                        if (line.length < 16_384) handler.post {
                            runCatching {
                                if (line != "POLL") lastConfig = JSONObject(line)
                                lastConfig?.let(::applyAndReport)
                            }.onFailure { println("ERROR ${it.message}"); System.out.flush() }
                        }
                    }
                } finally { handler.post { Looper.myLooper()?.quitSafely() } }
            }, "parts-commands").apply { isDaemon = true; start() }
            try { Looper.loop() } finally { try { ambient.close(); hardware.close() } finally { ownership.release(); lease.close() } }
        } catch (e: Throwable) { println("ERROR ${e.message}"); System.out.flush() }
    }
}

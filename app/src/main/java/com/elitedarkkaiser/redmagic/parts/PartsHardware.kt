/* SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 * Thermal profiles and tuning adapted from NubiaParts; lifecycle and root backend added here. */
package com.elitedarkkaiser.redmagic.parts

import android.system.Os
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal object PartsCommands {
    fun run(vararg args: String): String {
        val child = ProcessBuilder(*args).redirectErrorStream(true).start()
        val result = StringBuilder()
        val reader = Thread({ runCatching { child.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { if (result.length < 65_536) result.append(it).append('\n') }
        } } }).apply { isDaemon = true; start() }
        try {
            check(child.waitFor(8, TimeUnit.SECONDS)) { "Device command timed out" }
            reader.join(500)
            check(child.exitValue() == 0) { result.toString().trim().ifEmpty { "Device command failed" } }
            return result.toString().trim()
        } finally { if (child.isAlive) child.destroyForcibly() }
    }
}

/** Used only in the root process. Own mounts are identified by inode before removal. */
internal class PartsHardware(private val assets: File) : AutoCloseable {
    private val target = File("/vendor/etc/thermal-engine.conf")
    /** Thermal profiles live here, where the vendor thermal service may read them. */
    private val folder = File("/data/vendor/rmcc_parts").apply { mkdirs() }
    /** Recovery state: the vendor folder if it takes writes, else the app's own folder. */
    private val stateDir: File = listOf(folder, File(assets, "state")).firstOrNull { dir ->
        dir.mkdirs()
        runCatching { File(dir, ".probe").let { it.writeText("1"); it.delete() } }.isSuccess
    } ?: folder
    private val savedTuning = listOf(folder, stateDir).map { File(it, "tuning-before.json") }.firstOrNull { it.isFile }
        ?: File(stateDir, "tuning-before.json")
    private val chargeMarkers = listOf(folder, stateDir).distinct().map { File(it, "charge-owned") }
    private val chargeMarker = File(stateDir, "charge-owned")
    val stateLocation: String get() = stateDir.path

    /** Writes [value] to [file], naming the file if the kernel refuses it. */
    private fun put(file: File, value: String) {
        try { file.writeText(value) } catch (e: Exception) { throw IllegalStateException("${file.path}: ${e.message}", e) }
    }
    /** The recovery marker is a convenience; failing to write it must not stop the change itself. */
    private fun mark(value: String) { runCatching { chargeMarker.writeText(value) } }
    private val tuning = JSONObject(File(assets, "tuning.json").readText())
    private var namespaceReady = false
    private var selected = PerformanceProfile.BALANCED
    private var tuned = PerformanceProfile.BALANCED
    var separated = false; private set
    /** How charge separation was applied, or why it was refused, for the diagnostics report. */
    var chargeDetail = "Not requested"; private set
    private var chargeNode: File? = null
    private var chargeViaProperty = false
    private var refusedAt = 0L
    var active = PerformanceProfile.BALANCED; private set

    private fun namespace() {
        if (namespaceReady) return
        val result = PartsNative.enterMountNamespace()
        check(result == 0) { "Cannot access the system mount namespace ($result)" }
        namespaceReady = true
    }
    private fun sameFile(a: File, b: File): Boolean = runCatching {
        val x = Os.stat(a.path); val y = Os.stat(b.path)
        x.st_dev == y.st_dev && x.st_ino == y.st_ino
    }.getOrDefault(false)
    private fun ownMount() = PerformanceProfile.entries.filter { it != PerformanceProfile.BALANCED }
        .map { File(folder, "thermal-engine-${it.key}.conf") }.any { it.exists() && sameFile(it, target) }
    private fun restartThermal() {
        PartsCommands.run("setprop", "ctl.restart", "thermal-engine")
        val deadline = android.os.SystemClock.elapsedRealtime() + 3000
        do {
            if (PartsCommands.run("getprop", "init.svc.thermal-engine") == "running") return
            Thread.sleep(100)
        } while (android.os.SystemClock.elapsedRealtime() < deadline)
        error("Thermal service did not start; restoring stock settings")
    }

    /** Recover an interrupted previous session before accepting a new requested state. */
    /** Problems found while undoing an earlier session; reported, never fatal to the helper. */
    var recoveryWarnings: List<String> = emptyList(); private set

    /** Undo what an interrupted earlier session left behind. Never stops the helper from starting. */
    fun recover() {
        val warnings = mutableListOf<String>()
        chargeMarkers.filter { it.exists() }.forEach { marker ->
            runCatching {
                val owned = marker.readText().trim()
                if (owned == PROPERTY_ROUTE) PartsCommands.run("setprop", SEPARATION_PROPERTY, "0")
                val node = if (owned.startsWith("/")) File(owned) else File(DEFAULT_CHARGE_NODE)
                if (!reads(node, "1")) put(node, "1")
                check(reads(node, "1")) { "Could not restore normal charging" }
            }.onFailure { warnings += it.message ?: "Could not restore normal charging" }
            runCatching { marker.delete() }
        }
        runCatching {
            if (PerformanceProfile.entries.any { File(folder, "thermal-engine-${it.key}.conf").let {
                    it.isFile && sameFile(it, File("/proc/1/root/vendor/etc/thermal-engine.conf")) } }) {
                namespace()
                if (ownMount()) { check(PartsNative.unmountProfile() == 0) { "Could not remove our thermal profile" }; restartThermal() }
            }
        }.onFailure { warnings += it.message ?: "Could not restore the thermal profile" }
        warnings += restoreTuning()
        recoveryWarnings = warnings
    }
    fun performance(profile: PerformanceProfile, screenOn: Boolean) {
        var thermalTouched = false
        try {
            if (profile != selected || (profile != PerformanceProfile.BALANCED && !ownMount())) {
                namespace()
                if (ownMount()) { thermalTouched = true; check(PartsNative.unmountProfile() == 0) { "Could not remove our thermal profile" } }
                if (profile != PerformanceProfile.BALANCED) {
                    check(target.isFile) { "Thermal configuration is unavailable" }
                    check(PartsCommands.run("getprop", "init.svc.thermal-engine").isNotEmpty()) { "Thermal service is unavailable" }
                    val mountedElsewhere = File("/proc/self/mountinfo").readLines().any { it.split(' ').getOrNull(4) == target.canonicalPath }
                    check(!mountedElsewhere) { "Another thermal profile is active; return it to stock first" }
                    val source = File(folder, "thermal-engine-${profile.key}.conf")
                    File(assets, source.name).copyTo(source, overwrite = true)
                    PartsCommands.run("chmod", "644", source.path)
                    val label = Class.forName("android.os.SELinux").getMethod("getFileContext", String::class.java)
                        .invoke(null, target.path) as? String ?: error("Cannot read the thermal configuration label")
                    PartsCommands.run("chcon", label, source.path)
                    val result = PartsNative.bindProfile(source.path)
                    if (result == 0) thermalTouched = true
                    check(result == 0 && sameFile(source, target)) { "Could not mount the thermal profile ($result)" }
                }
                restartThermal()
                selected = profile
                active = profile
            }
            val wantedTuning = if (screenOn) profile else PerformanceProfile.BALANCED
            if (wantedTuning != tuned) {
                if (wantedTuning == PerformanceProfile.BALANCED) restoreTuning() else applyTuning(wantedTuning)
                tuned = wantedTuning
            }
        } catch (e: Throwable) {
            runCatching { if (ownMount()) { PartsNative.unmountProfile(); thermalTouched = true }; if (thermalTouched) restartThermal() }
            runCatching { restoreTuning() }
            selected = PerformanceProfile.BALANCED; active = selected; tuned = selected
            throw e
        }
    }
    private fun entries(profile: PerformanceProfile): List<Pair<String, String>> {
        val items = tuning.getJSONArray(profile.key)
        return (0 until items.length()).map { items.getJSONObject(it).let { row -> row.getString("path") to row.getString("value") } }
    }
    private fun applyTuning(profile: PerformanceProfile) {
        val entries = entries(profile)
        val critical = listOf("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq",
            "/sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq", "/sys/class/kgsl/kgsl-3d0/min_pwrlevel")
        check(critical.all { File(it).isFile }) { "Required performance nodes are unavailable" }
        if (!savedTuning.exists()) {
            val baseline = JSONObject()
            entries.map { it.first }.distinct().filter { File(it).exists() }.forEach { baseline.put(it, File(it).readText().trim()) }
            File(stateDir, "tuning-before.tmp").apply { put(this, baseline.toString()); check(renameTo(savedTuning)) }
        }
        val down = "/proc/sys/walt/sched_downmigrate"
        val up = "/proc/sys/walt/sched_upmigrate"
        entries.filter { it.first != down && it.first != up }.forEach { (path, value) -> if (File(path).exists()) put(File(path), value) }
        val upValue = entries.lastOrNull { it.first == up }?.second
        val downValue = entries.lastOrNull { it.first == down }?.second
        if (upValue != null && downValue != null && File(up).exists() && File(down).exists()) {
            // Never let downmigrate pass upmigrate mid-way: whichever order the kernel accepts.
            val applied = runCatching { put(File(down), downValue); put(File(up), upValue) }.isSuccess ||
                runCatching { put(File(up), upValue); put(File(down), downValue) }.isSuccess
            check(applied) { "The kernel refused the scheduler thresholds for ${profile.label}" }
        }
    }
    /**
     * Puts back the scheduler values saved before a raised profile, best effort: a value the kernel
     * refuses is skipped and reported. The saved copy is dropped either way, so one bad value cannot
     * fail every later start.
     */
    private fun restoreTuning(): List<String> {
        if (!savedTuning.isFile) return emptyList()
        val skipped = mutableListOf<String>()
        runCatching {
            val baseline = JSONObject(savedTuning.readText())
            val down = "/proc/sys/walt/sched_downmigrate"
            val up = "/proc/sys/walt/sched_upmigrate"
            baseline.keys().asSequence().filter { it != down && it != up }.forEach { path ->
                runCatching { put(File(path), baseline.getString(path)) }.onFailure { skipped += it.message ?: path }
            }
            if (baseline.has(up) && baseline.has(down)) {
                // The kernel refuses any step where downmigrate would exceed upmigrate, so try both orders.
                val upValue = baseline.getString(up)
                val downValue = baseline.getString(down)
                val restored = runCatching { put(File(down), downValue); put(File(up), upValue) }.isSuccess ||
                    runCatching { put(File(up), upValue); put(File(down), downValue) }.isSuccess
                if (!restored) skipped += "Scheduler migrate thresholds were left as they are"
            }
        }.onFailure { skipped += it.message ?: "Saved tuning was unreadable" }
        runCatching { savedTuning.delete() }
        tuned = PerformanceProfile.BALANCED
        return skipped
    }
    /** Every switch for battery charging this kernel exposes, the QTI one first. */
    private fun chargeNodes(): List<File> = buildList {
        add(File(DEFAULT_CHARGE_NODE))
        listOf("/sys/class/zte_power_supply", "/sys/class/power_supply").forEach { dir ->
            File(dir).listFiles().orEmpty().map { File(it, "battery_charging_enabled") }.forEach { add(it) }
        }
    }.filter { it.exists() }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }

    private fun reads(node: File, value: String) = runCatching { node.readText().trim() == value }.getOrDefault(false)

    fun charge(want: Boolean) {
        if (!want) refusedAt = 0L
        if (want) {
            // Already separated: only re-assert it if something (the charger policy) turned charging back on.
            chargeNode?.let { if (separated && reads(it, "0")) return }
            // A refusal is retried once a minute, not on every status poll.
            val now = android.os.SystemClock.elapsedRealtime()
            if (!separated && refusedAt != 0L && now - refusedAt < 60_000) error("Charge separation was refused; retrying shortly")
            val nodes = chargeNodes()
            check(nodes.isNotEmpty()) { "Charge separation is unavailable on this kernel" }
            val refused = mutableListOf<String>()
            for (node in nodes) {
                mark(node.path)
                val result = runCatching { node.writeText("0") }
                if (result.isSuccess && reads(node, "0")) {
                    chargeNode = node; chargeViaProperty = false; separated = true
                    chargeDetail = "Battery idle via ${node.path}"
                    return
                }
                refused += "${node.path}: " + (result.exceptionOrNull()?.message ?: "still reads ${runCatching { node.readText().trim() }.getOrNull()}")
            }
            // The ROM's own route, as its NubiaParts does it: init writes the QTI node.
            mark(PROPERTY_ROUTE)
            val viaInit = runCatching {
                // init only acts on a change, so a value already at 1 is cycled through 0 first.
                if (PartsCommands.run("getprop", SEPARATION_PROPERTY) == "1") PartsCommands.run("setprop", SEPARATION_PROPERTY, "0")
                PartsCommands.run("setprop", SEPARATION_PROPERTY, "1")
                Thread.sleep(400)
                reads(File(DEFAULT_CHARGE_NODE), "0")
            }.getOrDefault(false)
            if (viaInit) {
                chargeNode = File(DEFAULT_CHARGE_NODE); chargeViaProperty = true; separated = true
                chargeDetail = "Battery idle via $SEPARATION_PROPERTY"
                return
            }
            refused += "$SEPARATION_PROPERTY: no effect"
            runCatching { chargeMarker.delete() }
            separated = false; chargeNode = null; chargeViaProperty = false
            refusedAt = now
            chargeDetail = refused.joinToString(" | ")
            error("Charge separation was refused: ${refused.first()}")
        } else if (separated || chargeMarkers.any { it.exists() }) {
            refusedAt = 0L
            if (chargeViaProperty) runCatching { PartsCommands.run("setprop", SEPARATION_PROPERTY, "0") }
            val node = chargeNode ?: File(DEFAULT_CHARGE_NODE)
            if (!reads(node, "1")) put(node, "1")
            check(reads(node, "1")) { "Could not restore normal charging" }
            separated = false; chargeNode = null; chargeViaProperty = false
            chargeMarkers.forEach { runCatching { it.delete() } }
            chargeDetail = "Charging normally"
        }
    }
    fun skinTemperature(): Double? = File("/sys/class/thermal").listFiles().orEmpty()
        .filter { it.name.startsWith("thermal_zone") }
        .filter { runCatching { File(it, "type").readText().contains("skin", true) }.getOrDefault(false) }
        .mapNotNull { runCatching { File(it, "temp").readText().trim().toDouble().let { value -> if (kotlin.math.abs(value) > 200) value / 1000 else value } }.getOrNull() }
        .filter { it in -40.0..150.0 }.maxOrNull()

    override fun close() {
        var failure: Throwable? = null
        runCatching { performance(PerformanceProfile.BALANCED, false) }.onFailure { failure = it }
        runCatching { restoreTuning() }.onFailure { failure = it }
        runCatching { charge(false) }.onFailure { failure = it }
        failure?.let { throw it }
    }

    private companion object {
        const val DEFAULT_CHARGE_NODE = "/sys/class/qcom-battery/battery_charging_enabled"
        const val SEPARATION_PROPERTY = "sys.nubia.charge_separation"
        const val PROPERTY_ROUTE = "property"
    }
}

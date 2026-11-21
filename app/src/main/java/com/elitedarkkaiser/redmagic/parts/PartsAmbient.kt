/* SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 * Ambient behavior adapted from NubiaParts; netlink transport added for a root APK helper. */
package com.elitedarkkaiser.redmagic.parts

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import java.io.File

internal object PartsContext {
    val system: Context by lazy {
        val clazz = Class.forName("android.app.ActivityThread")
        val thread = clazz.getMethod("systemMain").invoke(null)
        clazz.getMethod("getSystemContext").invoke(thread) as Context
    }
}

internal class PartsAmbient(private val handler: Handler) : AutoCloseable {
    private val node = File("/proc/touchscreen/single_aod")
    private val context: Context get() = PartsContext.system
    private val sensors: SensorManager by lazy { context.getSystemService(SensorManager::class.java) }
    private var pickup: Sensor? = null
    private var listening = false
    private var ownedTap = false
    private var lastPulse = -1500L
    @Volatile private var running = true
    @Volatile private var tap = false
    private var interactive = true
    private var aodOn = true
    private var eventThread: Thread? = null
    var detail = "Off"; private set
    @Volatile private var lastPulseResult = "none yet"
    private var dozeSettings = ""
    private var dozeReadAt = 0L
    private var lastRequest = ""

    /** What ambient display was asked for and what the phone offers, for the diagnostics report. */
    val debug: String get() {
        val tapNode = if (node.exists()) runCatching { node.readText().trim() }.getOrElse { "unreadable" } else "missing"
        return "tap=$tap (single_aod=$tapNode) · lift listening=$listening sensor=${if (pickup != null) "found" else "not looked up"}" +
            " · screen=${if (interactive) "on" else "off"} · $dozeSettings · last pulse: $lastPulseResult"
    }

    private fun readDozeSettings() {
        val now = SystemClock.elapsedRealtime()
        if (now - dozeReadAt < 60_000 && dozeSettings.isNotEmpty()) return
        dozeReadAt = now
        dozeSettings = listOf("doze_enabled", "doze_always_on", "doze_pulse_on_pick_up", "doze_tap_gesture").joinToString(" ") { key ->
            "$key=" + runCatching { PartsCommands.run("settings", "--user", "current", "get", "secure", key).trim() }.getOrElse { "?" }
        }
    }
    private val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) { if (listening && event.values.firstOrNull()?.let { it != 0f } == true) pulse() }
    }
    fun configure(tapOn: Boolean, liftOn: Boolean, screenOn: Boolean, alwaysOn: Boolean) {
        interactive = screenOn; aodOn = alwaysOn
        val request = "$tapOn/$liftOn"
        if (request != lastRequest) { lastRequest = request; dozeReadAt = 0L }
        if (tapOn || liftOn) runCatching { readDozeSettings() }
        val messages = mutableListOf<String>()
        try {
            if (tapOn || ownedTap) {
                check(node.exists()) { "Tap-to-show node is unavailable" }
                node.writeText(if (tapOn) "1" else "0")
                ownedTap = tapOn
            }
            tap = tapOn
            if (tapOn) {
                startEvents()
                messages += "Tap to show"
            }
        } catch (e: Throwable) { tap = false; messages += e.message ?: "Tap to show failed" }
        try {
            if (liftOn && pickup == null) pickup = sensors.getSensorList(Sensor.TYPE_ALL)
                .firstOrNull { it.stringType == "zte.sensor.zte_pickup" && it.isWakeUpSensor }
            val want = liftOn && !screenOn
            if (want && !listening) {
                val sensor = pickup ?: error("Lift-to-show sensor is unavailable")
                check(sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)) { "Could not listen for lift gestures" }
                listening = true
            } else if (!want && listening) { sensors.unregisterListener(listener); listening = false }
            if (liftOn) messages += "Lift to show"
        } catch (e: Throwable) { messages += e.message ?: "Lift to show failed" }
        detail = messages.joinToString(" · ").ifEmpty { "Off" }
    }
    private fun startEvents() {
        if (eventThread?.isAlive == true) return
        val fd = PartsNative.openEvents()
        check(fd >= 0) { "Could not listen for screen taps ($fd)" }
        eventThread = Thread({
            try {
                while (running && tap) {
                    val event = PartsNative.nextEvent(fd) ?: continue
                    if (tap && event.lineSequence().any { it == "DEVPATH=/devices/platform/zte_touch" } &&
                        event.lineSequence().any { it == "single_tap=true" }) handler.post { if (tap) pulse() }
                }
            } finally { PartsNative.closeEvents(fd) }
        }, "parts-taps").apply { isDaemon = true; start() }
    }
    private fun pulse() {
        if (!running || interactive) return
        val showing = runCatching { PartsCommands.run("settings", "--user", "current", "get", "secure", "doze_always_on").trim() != "0" }.getOrDefault(aodOn)
        if (showing) { lastPulseResult = "skipped, always-on display is showing"; return }
        val screenOn = runCatching { context.getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(interactive)
        if (screenOn || SystemClock.elapsedRealtime() - lastPulse < 1500) return
        lastPulse = SystemClock.elapsedRealtime()
        lastPulseResult = runCatching {
            PartsCommands.run("am", "broadcast", "--user", "current", "-a", "com.android.systemui.doze.pulse", "-p", "com.android.systemui")
                .lines().lastOrNull { it.isNotBlank() }.orEmpty()
        }.getOrElse { "failed: ${it.message}" }
    }
    override fun close() {
        running = false; tap = false
        if (listening) runCatching { sensors.unregisterListener(listener) }
        if (ownedTap) runCatching { node.writeText("0") }
    }
}

/* SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 * Policies adapted from devnotcc/android_device_nubia_nx789j NubiaParts. */
package com.elitedarkkaiser.redmagic.parts

internal enum class PerformanceProfile(val key: String, val label: String, val detail: String) {
    BALANCED("balanced", "Balanced", "Stock limits · skin throttling from 43 °C"),
    PERFORMANCE("performance", "Performance", "Faster ramp-up · skin throttling from 47 °C"),
    EXTREME("extreme", "Extreme", "High clock floors · skin throttling from 52 °C"),
    ULTRA("ultra", "Ultra", "Maximum clock floors · skin throttling from 60 °C");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.key == value } ?: BALANCED }
}
internal enum class SeparationMode(val key: String, val label: String) {
    OFF("off", "Off"), GAMING("gaming", "In games and raised performance modes"), ALWAYS("always", "Whenever a charger is connected");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.key == value } ?: OFF }
}

internal object PartsPolicy {
    fun inWindow(minute: Int, start: Int, end: Int): Boolean = start != end &&
        if (start < end) minute >= start && minute < end else minute >= start || minute < end
    fun aodAction(enabled: Boolean, minute: Int, start: Int, end: Int, owned: Boolean, aodOn: Boolean): Int? {
        val inside = enabled && inWindow(minute, start, end)
        return if (inside && !owned && aodOn) 0 else if (!inside && owned) 1 else null
    }
    fun games(value: String?): Set<String> = value.orEmpty().split(';').map { it.substringBefore('=').trim() }
        .filter { it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }.toSet()
    fun chargingPower(plugged: Boolean, charging: Boolean, maxUa: Long, maxUv: Long, currentUa: Long, voltageMv: Long): Long {
        if (!plugged || !charging) return 0
        val negotiated = maxUa.coerceIn(0, 100_000_000) / 1000 * (maxUv.coerceIn(0, 100_000_000) / 1000)
        val measured = kotlin.math.abs(currentUa.coerceIn(-100_000_000, 100_000_000)) / 1000 * voltageMv.coerceIn(0, 100_000)
        return maxOf(negotiated, measured)
    }
    class Guards {
        var hot = false; private set
        var low = false; private set
        var fast = false; private set
        fun update(tempTenths: Int?, percent: Int?, powerUw: Long, plugged: Boolean) {
            hot = tempTenths == null || tempTenths >= if (hot) 480 else 550
            low = percent == null || percent < if (low) 25 else 20
            fast = plugged && powerUw >= if (fast) 8_000_000 else 15_000_000
        }
        fun profile(selected: PerformanceProfile, magicBound: Boolean, magicOn: Boolean): PerformanceProfile =
            if (hot || (magicBound && !magicOn)) PerformanceProfile.BALANCED else selected
    }
    /** Negotiation delay, low-battery guard and sustained-drain re-arm, using elapsed time. */
    class Separation {
        private var connected = false
        private var armedAt = 0L
        private var lastCheck = 0L
        private var lastRearm = Long.MIN_VALUE / 2
        private var drains = 0
        fun wanted(now: Long, plugged: Boolean, low: Boolean, mode: SeparationMode,
            inGame: Boolean, profile: PerformanceProfile, actual: Boolean, currentMa: Int?): Boolean {
            if (plugged && !connected) { armedAt = now; drains = 0; lastCheck = now }
            connected = plugged
            val need = plugged && !low && (mode == SeparationMode.ALWAYS ||
                (mode == SeparationMode.GAMING && (inGame || profile != PerformanceProfile.BALANCED)))
            if (!need) { drains = 0; return false }
            if (actual && now - lastCheck >= 10_000) {
                lastCheck = now
                drains = if (currentMa != null && currentMa < -400) drains + 1 else 0
                if (drains >= 2 && now - lastRearm >= 120_000) {
                    armedAt = now; lastRearm = now; drains = 0
                }
            }
            return now - armedAt >= 15_000
        }
    }
    /**
     * Keeps "in a game" true for a short time after the game leaves the top. Recents, the
     * notification shade and app switching briefly put another app on top; without this the fan
     * floor would switch off and on with every sample.
     */
    class GameHold(private val graceMs: Long = 8_000) {
        private var lastSeen = Long.MIN_VALUE / 2
        fun update(now: Long, inGame: Boolean, screenOn: Boolean): Boolean {
            if (!screenOn) { lastSeen = Long.MIN_VALUE / 2; return false }
            if (inGame) lastSeen = now
            return now - lastSeen < graceMs
        }
    }
    fun fanFloor(screenOn: Boolean, game: Boolean, gamesEnabled: Boolean, fast: Boolean, fastEnabled: Boolean,
        profile: PerformanceProfile, level: Int, pausedForCall: Boolean): Int {
        if (pausedForCall && profile == PerformanceProfile.BALANCED) return 0
        val want = (screenOn && (profile != PerformanceProfile.BALANCED || (game && gamesEnabled))) || (fast && fastEnabled)
        if (!want) return 0
        return if (screenOn && profile in listOf(PerformanceProfile.EXTREME, PerformanceProfile.ULTRA)) 5 else level.coerceIn(1, 5)
    }
}

/** Tracks the normal fan request underneath temporary game/charging/performance minimums. */
internal class FanOverrideState {
    @Volatile var floor = 0; private set
    var base = 0; private set
    private var lastNonzero = 3
    fun transition(minimum: Int, snapshot: Int? = null): Int {
        val next = minimum.coerceIn(0, 5)
        if (floor == 0 && next > 0) { base = (snapshot ?: 0).coerceIn(0, 5); if (base > 0) lastNonzero = base }
        floor = next
        return maxOf(base, floor)
    }
    fun request(level: Int): Int {
        val requested = level.coerceIn(0, 5)
        if (floor > 0) { base = requested; if (base > 0) lastNonzero = base }
        return maxOf(requested, floor)
    }
    fun snapshot(enabled: Boolean, level: Int): Pair<Boolean, Int> = if (floor > 0) (base > 0) to base else enabled to level
    fun enabled(on: Boolean): Int? = if (floor == 0) null else request(if (on) lastNonzero else 0)
}

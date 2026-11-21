package com.elitedarkkaiser.redmagic.parts

import org.junit.Assert.*
import org.junit.Test

class PartsPolicyTest {
    @Test fun gameHoldRidesOutRecentsFlickerAndReleasesAfterGrace() {
        val hold = PartsPolicy.GameHold(graceMs = 8_000)
        assertFalse(hold.update(0, inGame = false, screenOn = true))
        assertTrue(hold.update(1_000, inGame = true, screenOn = true))
        // Recents alternates game / launcher samples: the fan must stay on.
        for (t in 2_000L..7_000L step 1_000) assertTrue(hold.update(t, inGame = t % 2_000 == 0L, screenOn = true))
        assertTrue(hold.update(13_000, inGame = false, screenOn = true))
        assertFalse(hold.update(14_001, inGame = false, screenOn = true))
        assertTrue(hold.update(15_000, inGame = true, screenOn = true))
        assertFalse(hold.update(15_500, inGame = true, screenOn = false))
        assertFalse(hold.update(16_000, inGame = false, screenOn = true))
    }
    @Test fun overnightScheduleHasInclusiveStartExclusiveEndAndEqualTimesDoNothing() {
        assertFalse(PartsPolicy.inWindow(22 * 60, 23 * 60, 7 * 60))
        assertTrue(PartsPolicy.inWindow(23 * 60, 23 * 60, 7 * 60))
        assertTrue(PartsPolicy.inWindow(3 * 60, 23 * 60, 7 * 60))
        assertFalse(PartsPolicy.inWindow(7 * 60, 23 * 60, 7 * 60))
        assertFalse(PartsPolicy.inWindow(100, 100, 100))
        assertTrue(PartsPolicy.inWindow(12 * 60, 10 * 60, 14 * 60))
    }
    @Test fun scheduleRestoresOnlyItsOwnChangeIncludingWhenDisabled() {
        assertEquals(0, PartsPolicy.aodAction(true, 60, 0, 420, false, true))
        assertNull(PartsPolicy.aodAction(true, 60, 0, 420, false, false))
        assertNull(PartsPolicy.aodAction(true, 60, 0, 420, true, true))
        assertNull(PartsPolicy.aodAction(true, 500, 0, 420, false, false))
        assertEquals(1, PartsPolicy.aodAction(true, 500, 0, 420, true, false))
        assertEquals(1, PartsPolicy.aodAction(false, 60, 0, 420, true, false))
    }
    @Test fun temperatureGuardRequiresCoolingBeforeReleasingRaisedModes() {
        val guards = PartsPolicy.Guards()
        guards.update(540, 80, 0, false)
        assertEquals(PerformanceProfile.ULTRA, guards.profile(PerformanceProfile.ULTRA, false, false))
        guards.update(550, 80, 0, false)
        assertEquals(PerformanceProfile.BALANCED, guards.profile(PerformanceProfile.ULTRA, false, false))
        guards.update(500, 80, 0, false); assertTrue(guards.hot)
        guards.update(479, 80, 0, false); assertFalse(guards.hot)
        guards.update(null, 80, 0, false); assertTrue(guards.hot)
    }
    @Test fun magicKeyGatesTheSelectedProfileAndReleaseReturnsToBalanced() {
        val guards = PartsPolicy.Guards().apply { update(300, 80, 0, false) }
        assertEquals(PerformanceProfile.BALANCED, guards.profile(PerformanceProfile.EXTREME, true, false))
        assertEquals(PerformanceProfile.EXTREME, guards.profile(PerformanceProfile.EXTREME, true, true))
        assertEquals(PerformanceProfile.BALANCED, guards.profile(PerformanceProfile.EXTREME, true, false))
    }
    @Test fun fastChargingUsesNegotiatedOrMeasuredPowerAndHysteresis() {
        assertEquals(27_000_000L, PartsPolicy.chargingPower(true, true, 3_000_000, 9_000_000, -1_000_000, 4500))
        assertEquals(18_000_000L, PartsPolicy.chargingPower(true, true, 0, 0, -4_000_000, 4500))
        assertEquals(0L, PartsPolicy.chargingPower(false, true, 3_000_000, 9_000_000, 0, 4500))
        val guards = PartsPolicy.Guards()
        guards.update(300, 80, 14_999_999, true); assertFalse(guards.fast)
        guards.update(300, 80, 15_000_000, true); assertTrue(guards.fast)
        guards.update(300, 80, 8_000_000, true); assertTrue(guards.fast)
        guards.update(300, 80, 7_999_999, true); assertFalse(guards.fast)
        guards.update(300, 80, 30_000_000, false); assertFalse(guards.fast)
    }
    @Test fun lowBatteryCannotBeBypassedUntilItRecoversTo25Percent() {
        val guards = PartsPolicy.Guards()
        guards.update(300, 19, 0, true); assertTrue(guards.low)
        guards.update(300, 24, 0, true); assertTrue(guards.low)
        guards.update(300, 25, 0, true); assertFalse(guards.low)
        guards.update(300, 20, 0, true); assertFalse(guards.low)
        guards.update(300, null, 0, true); assertTrue(guards.low)
    }
    private fun separated(engine: PartsPolicy.Separation, now: Long, plugged: Boolean = true,
        low: Boolean = false, mode: SeparationMode = SeparationMode.ALWAYS, game: Boolean = false,
        profile: PerformanceProfile = PerformanceProfile.BALANCED, actual: Boolean = false, ma: Int? = 0) =
        engine.wanted(now, plugged, low, mode, game, profile, actual, ma)
    @Test fun bypassWaitsForNegotiationAndDisconnectOrLowChargeImmediatelyCancelsIt() {
        val engine = PartsPolicy.Separation()
        assertFalse(separated(engine, 1000))
        assertFalse(separated(engine, 15_999))
        assertTrue(separated(engine, 16_000))
        assertFalse(separated(engine, 17_000, low = true))
        assertFalse(separated(engine, 18_000, plugged = false))
        assertFalse(separated(engine, 19_000))
        assertTrue(separated(engine, 34_000))
    }
    @Test fun gamingBypassRequiresAGameOrAnActuallyRaisedProfile() {
        val engine = PartsPolicy.Separation()
        assertFalse(separated(engine, 0, mode = SeparationMode.GAMING))
        assertFalse(separated(engine, 20_000, mode = SeparationMode.GAMING))
        assertTrue(separated(engine, 20_001, mode = SeparationMode.GAMING, game = true))
        assertTrue(separated(engine, 20_002, mode = SeparationMode.GAMING, profile = PerformanceProfile.PERFORMANCE))
        assertFalse(separated(engine, 20_003, mode = SeparationMode.OFF, game = true))
    }
    @Test fun sustainedDrainRearmsChargingAtMostOnceEveryTwoMinutes() {
        val engine = PartsPolicy.Separation()
        assertFalse(separated(engine, 0))
        assertTrue(separated(engine, 15_000, actual = true, ma = -500))
        assertTrue(separated(engine, 15_001, actual = true, ma = -500))
        assertFalse(separated(engine, 25_000, actual = true, ma = -500))
        assertFalse(separated(engine, 39_999))
        assertTrue(separated(engine, 40_000))
        assertTrue(separated(engine, 50_000, actual = true, ma = -500))
        assertTrue(separated(engine, 60_000, actual = true, ma = -500))
        assertFalse(separated(engine, 145_000, actual = true, ma = -500))
    }
    @Test fun gameCoolingStopsWithScreenWhileFastChargingCoolingContinues() {
        fun floor(screen: Boolean, game: Boolean = false, fast: Boolean = false, profile: PerformanceProfile = PerformanceProfile.BALANCED) =
            PartsPolicy.fanFloor(screen, game, true, fast, true, profile, 3, false)
        assertEquals(3, floor(true, game = true))
        assertEquals(0, floor(false, game = true))
        assertEquals(3, floor(false, fast = true))
        assertEquals(5, floor(true, profile = PerformanceProfile.ULTRA))
        assertEquals(0, floor(false, profile = PerformanceProfile.ULTRA))
        assertEquals(0, floor(true))
    }
    @Test fun gameSpaceParsingUsesPackagesAndRejectsMalformedEntries() {
        assertEquals(setOf("com.roblox.client", "com.pixel.gun3d"), PartsPolicy.games("com.roblox.client=2;com.pixel.gun3d=1;broken;$;"))
    }
}

package com.elitedarkkaiser.redmagic

import org.junit.Assert.assertEquals
import org.junit.Test

class LogoLedValueTest {
    @Test fun eachAnimationLoadsItsOwnProgramForTheSameColour() {
        assertEquals("0x67", HardwareController.logoLedValue("steady", 1))
        assertEquals("0x77", HardwareController.logoLedValue("breathe", 1))
        assertEquals("0x87", HardwareController.logoLedValue("flashing", 1))
        assertEquals("0x60", HardwareController.logoLedValue("steady", 9))
        assertEquals("0x70", HardwareController.logoLedValue("breathe", 9))
        assertEquals("0x85", HardwareController.logoLedValue("flashing", 7))
    }

    @Test fun unknownValuesFallBackToSteadyGreen() {
        assertEquals("0x63", HardwareController.logoLedValue("flow", 42))
    }
}

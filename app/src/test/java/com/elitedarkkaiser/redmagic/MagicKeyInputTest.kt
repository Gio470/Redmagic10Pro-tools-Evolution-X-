package com.elitedarkkaiser.redmagic

import org.junit.Assert.*
import org.junit.Test

class MagicKeyInputTest {
    // The actual device names and SW_PEN_INSERTED report supplied by the NX789J user.
    private val devices = """
        add device 1: /dev/input/event10
          name: "Redmagic Tools merged touch"
          events:
            KEY (0001): 0145 014a
            ABS (0003): 002f : value 11, min 0, max 11
        add device 4: /dev/input/event7
          name: "gdix_input_agent"
          events:
            KEY (0001): 008f 0145 014a 0162
        add device 5: /dev/input/event6
          name: "nubia_tgk_aw_sar1_ch0"
          events:
            KEY (0001): 003b 0042
        add device 9: /dev/input/event2
          name: "gpio-keys_nubia"
          events:
            SW  (0005): 000f*
          input props:
            <none>
        add device 11: /dev/input/event0
          name: "gpio-keys"
          events:
            KEY (0001): 0073
    """.trimIndent()

    @Test fun findsTheSliderWithoutTouchingTheTouchscreenOrShoulderDevices() {
        assertEquals(MagicKeyInput.Device("/dev/input/event2", "gpio-keys_nubia", 1), MagicKeyInput.device(devices))
    }
    @Test fun releasedStateAndDifferentEventNumberAreDiscoveredEachTime() {
        val dump = devices.replace("000f*", "000f").replace("/dev/input/event2", "/dev/input/event12")
        assertEquals(MagicKeyInput.Device("/dev/input/event12", "gpio-keys_nubia", 0), MagicKeyInput.device(dump))
    }
    @Test fun aStylusSwitchOrUnrelatedGpioDeviceIsNeverUsedAsTheSlider() {
        assertNull(MagicKeyInput.device(devices.replace("gpio-keys_nubia", "Wacom pen")))
        assertNull(MagicKeyInput.device(devices.replace("SW  (0005): 000f*", "KEY (0001): 000f")))
        assertNull(MagicKeyInput.device("root: permission denied"))
        assertNull(MagicKeyInput.device("name: \"gpio-keys_nubia\"\nSW (0005): 000f*"))
    }
    @Test fun onlySliderSwitchEventsAreAccepted() {
        assertEquals(1, MagicKeyInput.value("[ 123.456] EV_SW SW_PEN_INSERTED 00000001"))
        assertEquals(0, MagicKeyInput.value("/dev/input/event2: 0005 000f 00000000"))
        assertNull(MagicKeyInput.value("EV_KEY KEY_F8 00000001"))
        assertNull(MagicKeyInput.value("EV_SW SW_LID 00000001"))
        assertNull(MagicKeyInput.value("EV_SW SW_PEN_INSERTED ffffffff"))
    }
    @Test fun startingWhileTheSliderIsOnNeverLaunchesAndDuplicateReportsAreIgnored() {
        val edges = MagicKeyInput.Edges(1)
        assertNull(edges.update(1, false))
        assertEquals(false, edges.update(0, false))
        assertNull(edges.update(0, false))
        assertEquals(true, edges.update(1, false))
        assertNull(edges.update(1, false))
    }
    @Test fun reversingTheActivationPositionStillRequiresRealMovement() {
        val edges = MagicKeyInput.Edges(1)
        assertNull(edges.update(1, true))
        assertEquals(true, edges.update(0, true))
        assertEquals(false, edges.update(1, true))
    }
}

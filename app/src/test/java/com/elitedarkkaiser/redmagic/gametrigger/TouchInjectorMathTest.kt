package com.elitedarkkaiser.redmagic.gametrigger

import android.view.Surface
import com.elitedarkkaiser.redmagic.gametrigger.TouchInjectorMath.Axis
import org.junit.Assert.*
import org.junit.Test

class TouchInjectorMathTest {
    private val listing = """
        add device 1: /dev/input/event5
          name:     "nubia_tgk_aw_sar0_ch0"
          events:
            KEY (0001): 0042
        add device 2: /dev/input/event3
          name:     "goodix_ts"
          events:
            KEY (0001): 014a
            ABS (0003): 002f  : value 0, min 0, max 9, fuzz 0, flat 0, resolution 0
                        0030  : value 0, min 0, max 255, fuzz 0, flat 0, resolution 0
                        0035  : value 0, min 0, max 12159, fuzz 0, flat 0, resolution 0
                        0036  : value 0, min 0, max 26879, fuzz 0, flat 0, resolution 0
                        0039  : value 0, min 0, max 65535, fuzz 0, flat 0, resolution 0
                        003a  : value 0, min 0, max 1023, fuzz 0, flat 0, resolution 0
          input props:
            INPUT_PROP_DIRECT
    """.trimIndent()

    @Test fun findsTheTouchscreenAndItsAxesButNotTheTriggerSensor() {
        val devices = TouchInjectorMath.parseDevices(listing)
        assertEquals(2, devices.size)
        val touch = devices.single { it.isTouchscreen }
        assertEquals("/dev/input/event3", touch.path)
        assertEquals("goodix_ts", touch.name)
        assertEquals(Axis(0, 9), touch.abs[TouchInjectorMath.ABS_MT_SLOT])
        assertEquals(Axis(0, 12159), touch.abs[TouchInjectorMath.ABS_MT_POSITION_X])
        assertEquals(Axis(0, 1023), touch.abs[TouchInjectorMath.ABS_MT_PRESSURE])
        assertFalse(devices.first().isTouchscreen)
    }

    private val x = Axis(0, 12159)
    private val y = Axis(0, 26879)

    @Test fun portraitScalesStraightToPanelUnits() {
        assertEquals(0 to 0, TouchInjectorMath.toRaw(0, 0, Surface.ROTATION_0, 1216, 2688, x, y))
        assertEquals(6080 to 13440, TouchInjectorMath.toRaw(608, 1344, Surface.ROTATION_0, 1216, 2688, x, y))
    }

    @Test fun landscapeUndoesTheRotationBeforeScaling() {
        // Phone turned left (90): the display's top-left is the panel's top-right.
        val (rx, ry) = TouchInjectorMath.toRaw(0, 0, Surface.ROTATION_90, 2688, 1216, x, y)
        assertTrue(rx > 12100)
        assertEquals(0, ry)
        // Turned right (270): the display's top-left is the panel's bottom-left.
        val (lx, ly) = TouchInjectorMath.toRaw(0, 0, Surface.ROTATION_270, 2688, 1216, x, y)
        assertEquals(0, lx)
        assertTrue(ly > 26800)
    }

    @Test fun pointsOutsideTheDisplayStayOnThePanel() {
        val (rx, ry) = TouchInjectorMath.toRaw(5000, -20, Surface.ROTATION_0, 1216, 2688, x, y)
        assertTrue(rx in 0..12159)
        assertTrue(ry in 0..26879)
    }

    @Test fun mergedTouchscreenCopiesThePanelWithTwoMoreSlots() {
        val panel = TouchInjectorMath.parseDevices(listing).first { it.isTouchscreen }
        // Plain text checks: org.json is only a stub in JVM unit tests.
        val json = TouchInjectorMath.registerJson(panel.abs)
        assertTrue(json.contains(""""command":"register""""))
        assertTrue(json.contains(""""name":"${TouchInjectorMath.MERGED_NAME}""""))
        // The slot axis gets two more slots; the panel's other axes are copied as they are.
        assertTrue(json.contains("""{"code":47,"info":{"value":0,"minimum":0,"maximum":${9 + TouchInjectorMath.EXTRA_SLOTS},"""))
        assertTrue(json.contains("""{"code":53,"info":{"value":0,"minimum":0,"maximum":12159,"""))
        assertTrue(json.contains("""{"type":103,"data":[47,48,53,54,57,58]}"""))
        // Marked direct, so Android takes it for a touchscreen.
        assertTrue(json.contains("""{"type":110,"data":[1]}"""))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
    }

    @Test fun idcKeepsThePanelsSettingsAndMakesItInternal() {
        val idc = TouchInjectorMath.idc("touch.size.scale = 3\ndevice.internal = 0\n")
        assertTrue(idc.contains("touch.size.scale = 3"))
        assertFalse(idc.contains("device.internal = 0"))
        assertTrue(idc.contains("device.internal = 1"))
        assertTrue(TouchInjectorMath.idc(null).contains("touch.deviceType = touchScreen"))
    }

    @Test fun recognisesWhenAndroidUsesTheMergedDevice() {
        val good = """
            Input Reader State (Nums of device: 2):
              Device 3: goodix_ts
                IsExternal: false
                Touch Input Mapper (mode=DIRECT):
              Device 9: ${TouchInjectorMath.MERGED_NAME}
                IsExternal: false
                Touch Input Mapper (mode=DIRECT):
        """.trimIndent()
        assertTrue(TouchInjectorMath.mergedDeviceUsable(good))
        val external = good.substringBefore("  Device 9:") +
            "  Device 9: ${TouchInjectorMath.MERGED_NAME}\n    IsExternal: true\n    Touch Input Mapper (mode=DIRECT):\n"
        assertFalse(TouchInjectorMath.mergedDeviceUsable(external))
        assertFalse(TouchInjectorMath.mergedDeviceUsable("Input Reader State:\n  Device 3: goodix_ts\n"))
    }

    @Test fun findsEveryTouchscreenAndroidReadsFingersFrom() {
        val dump = """
            Event Hub State:
              Devices:
                7: gdix_input_agent
            Input Reader State (Nums of device: 5):
              Device 2: gdix_input_agent
                IsExternal: false
                Touch Input Mapper (mode=DIRECT):
              Device 4: goodix_ts
                IsExternal: false
                Touch Input Mapper (mode=DIRECT):
              Device 6: nubia_tgk_aw_sar0_ch0
                Keyboard Input Mapper:
              Device 9: ${TouchInjectorMath.MERGED_NAME}
                Touch Input Mapper (mode=DIRECT):
              Device 11: some_pen
                Touch Input Mapper (mode=DISABLED):
        """.trimIndent()
        assertEquals(setOf("gdix_input_agent", "goodix_ts"), TouchInjectorMath.androidTouchDevices(dump))
        assertTrue(TouchInjectorMath.androidTouchDevices("nothing here").isEmpty())
    }

    @Test fun registerTakesTheMergedSlotCount() {
        val panel = TouchInjectorMath.parseDevices(listing).first { it.isTouchscreen }
        assertTrue(TouchInjectorMath.registerJson(panel.abs, 22).contains(
            """{"code":47,"info":{"value":0,"minimum":0,"maximum":21,"""))
    }

    @Test fun nx789jHasOneSourceTouchscreenPlusOurVirtualOutput() {
        // Relevant InputReader blocks from the user's Android 16 dumpsys input.
        val dump = """
            Input Reader State (Nums of device: 9):
              Device 11: Redmagic Tools merged touch
                EventHub Devices: [ 13 ]
                IsExternal: false
                Sources: TOUCHSCREEN
                Touch Input Mapper (mode - DIRECT):
              Device 7: gdix_input_agent
                EventHub Devices: [ 3 ]
                IsExternal: false
                Sources: KEYBOARD | TOUCHSCREEN
                Keyboard Input Mapper:
                Touch Input Mapper (mode - DIRECT):
              Device 3: goodix_fp
                EventHub Devices: [ 9 5 4 ]
                Sources: KEYBOARD
                Keyboard Input Mapper:
              Device 12: disabled_touch
                Touch Input Mapper (mode - DISABLED):
        """.trimIndent()
        assertEquals(setOf("gdix_input_agent"), TouchInjectorMath.androidTouchDevices(dump))
        assertTrue(TouchInjectorMath.mergedDeviceUsable(dump))
    }
}


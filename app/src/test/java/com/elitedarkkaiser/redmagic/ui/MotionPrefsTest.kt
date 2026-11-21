package com.elitedarkkaiser.redmagic.ui

import org.junit.Assert.*
import org.junit.Test

class MotionPrefsTest {
    @Test fun unknownOrMissingPresetsFallBackToQBouncy() {
        assertEquals(MotionPrefs.Preset.Q_BOUNCY, MotionPrefs.Preset.fromKey(null))
        assertEquals(MotionPrefs.Preset.Q_BOUNCY, MotionPrefs.Preset.fromKey("future"))
        MotionPrefs.Preset.entries.forEach { assertEquals(it, MotionPrefs.Preset.fromKey(it.key)) }
    }

    @Test fun offSnapsAndQBouncyIsSpringierThanSoft() {
        MotionPrefs.Role.entries.forEach { role ->
            listOf(true, false).forEach { pressing ->
                assertNull(MotionPrefs.spring(MotionPrefs.Preset.OFF, role, pressing))
                val soft = MotionPrefs.spring(MotionPrefs.Preset.SOFT, role, pressing)!!
                val bouncy = MotionPrefs.spring(MotionPrefs.Preset.Q_BOUNCY, role, pressing)!!
                assertTrue(bouncy.first < soft.first)
                assertTrue(bouncy.first < 1f)
            }
        }
    }
}

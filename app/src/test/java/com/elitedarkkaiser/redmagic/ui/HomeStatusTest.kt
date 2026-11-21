package com.elitedarkkaiser.redmagic.ui

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class HomeStatusTest {
    private fun context() = ContextThemeWrapper(RuntimeEnvironment.getApplication(),
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
    @Test fun readyNeedsOnlyTheSupportedDeviceAndLiveRootAndClearsWhileRechecking() {
        val context = context()
        val strip = CompatStrip(context, M3(context))
        assertFalse(strip.isReady)
        strip.applyStatus(true, "RedMagic 10 Pro", true)
        assertTrue(strip.isReady)
        assertTrue(strip.contentDescription.contains("Ready for control"))
        strip.beginCheck()
        assertFalse(strip.isReady)
        strip.applyStatus(true, "RedMagic 10 Pro", false)
        assertFalse(strip.isReady)
        assertTrue(strip.contentDescription.contains("Root access needed"))
        strip.applyStatus(true, "RedMagic 10 Pro", true)
        assertTrue(strip.isReady)
    }

    @Test fun rootRevocationAndUnsupportedDeviceClearReadiness() {
        val context = context()
        val strip = CompatStrip(context, M3(context))
        strip.applyStatus(true, "RedMagic 10 Pro", true)
        strip.setRoot(false, "Revoked")
        assertFalse(strip.isReady)
        assertTrue(strip.contentDescription.contains("Root access needed"))
        strip.setRoot(true, "Granted again")
        assertTrue(strip.isReady)
        strip.applyStatus(false, "Other phone", true)
        assertFalse(strip.isReady)
        assertTrue(strip.contentDescription.contains("Device not supported"))
    }

    @Test fun homeKeepsRootReadingsHiddenUntilRootIsConfirmed() {
        val context = context()
        val result = HomeTabUi.create(HomeTabDeps(
            scrollTabContainer = { LinearLayout(context).apply { orientation = LinearLayout.VERTICAL } },
            openTab = {}, isRedMagic10Pro = { true }, deviceCompatModelSummary = { "NX789J" },
            hasRootAccess = { false }, recheckDeviceCompatibility = {}
        ))
        assertFalse(result.refs.compat.isReady)
        assertEquals(1, cards(result.view).size)
        assertFalse(contains(result.view, result.refs.cpuUsageValue))
        assertFalse(contains(result.view, result.refs.tempGauge))

        result.refs.setAvailability(true, true)
        assertEquals(4, cards(result.view).size)
        assertTrue(contains(result.view, result.refs.cpuUsageValue))
        assertTrue(contains(result.view, result.refs.tempGauge))
        result.refs.cpuUsageValue.text = "42%"
        result.refs.cpuClusters.visibility = View.VISIBLE
        result.refs.ramValue.text = "51%"

        // Repeated polls leave shells and live data alone. Revocation removes readings; restored
        // access reuses their views, including CPU expansion, without duplicate-parent failures.
        val existing = cards(result.view)
        result.refs.setAvailability(true, true)
        assertEquals(existing, cards(result.view))
        result.refs.setAvailability(false, true)
        assertEquals(1, cards(result.view).size)
        result.refs.setAvailability(true, false)
        assertEquals(3, cards(result.view).size)
        result.refs.setAvailability(true, true)
        assertEquals(4, cards(result.view).size)
        assertEquals("42%", result.refs.cpuUsageValue.text.toString())
        assertEquals("51%", result.refs.ramValue.text.toString())
        assertEquals(View.VISIBLE, result.refs.cpuClusters.visibility)
    }

    private fun contains(root: View, target: View): Boolean = root === target ||
        (root is ViewGroup && (0 until root.childCount).any { contains(root.getChildAt(it), target) })

    private fun cards(view: View): List<ExpressiveCard> = buildList {
        if (view is ExpressiveCard) add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(cards(view.getChildAt(index)))
    }
}

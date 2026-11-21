package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class LightingControlsTest {
    @Test fun hardwareHasFiveIndependentLightingCardsWithTheirOwnMasters() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        var calls = false
        var charging = false
        var game = false
        var fan = false
        var logo = false
        var opened = ""
        val deps = LightingTabDeps(
            scrollTabContainer = { LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL } },
            getFanLedEnabled = { fan }, setFanLedEnabled = { fan = it },
            getLogoLedEnabled = { logo }, setLogoLedEnabled = { logo = it },
            showFanLedDialog = { opened = "fan" }, showLogoLedDialog = { opened = "logo" },
            getGameModeEnabled = { game }, setGameModeEnabled = { game = it },
            showGameModeAppPicker = {}, gameModeEditor = { LinearLayout(activity) }, gameModeAppsSummary = { "No apps selected" },
            getChargingLedEnabled = { charging }, setChargingLedEnabled = { charging = it },
            chargingEditor = { LinearLayout(activity) },
            getCallLightingEnabled = { calls }, setCallLightingEnabled = { calls = it },
            getPauseFanDuringCalls = { false }, setPauseFanDuringCalls = {},
            callLightingEditor = { LinearLayout(activity) }
        )
        val result = LightingTabUi.create(activity, deps)
        val sections = (0 until result.view.childCount).map { result.view.getChildAt(it) }
            .filterIsInstance<SwitchSection>()
        assertEquals(listOf("Game Mode", "Call lighting", "Charging"), sections.map { it.title })
        val cards = result.lightCards + sections.map { SectionPages.item(activity, SectionPages.entry(it)) }
        assertEquals(listOf("Fan LED", "Logo LED", "Game Mode", "Call lighting", "Charging"), cards.map { it.title })
        assertTrue(cards.all { it.master != null })
        assertTrue(cards.all { it.onClick != null })
        assertFalse(cards.any { it.title == "LED zones" })

        fun card(name: String) = cards.single { it.title == name }
        card("Fan LED").master!!.write(true)
        assertTrue(fan)
        assertFalse(logo)
        card("Logo LED").master!!.write(true)
        assertTrue(logo)
        card("Game Mode").master!!.write(true)
        assertTrue(game)
        card("Call lighting").master!!.write(true)
        assertTrue(calls)
        assertFalse(charging)
        card("Charging").master!!.write(true)
        assertTrue(charging)
        card("Fan LED").onClick!!.invoke()
        assertEquals("fan", opened)
        card("Logo LED").onClick!!.invoke()
        assertEquals("logo", opened)
        card("Fan LED").master!!.write(false)
        assertFalse(fan)
        assertTrue(logo)
        assertTrue(card("Logo LED").master!!.read())
        activity.finish()
    }
}

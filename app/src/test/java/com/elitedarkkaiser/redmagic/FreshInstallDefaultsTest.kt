package com.elitedarkkaiser.redmagic

import android.app.Application
import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import com.elitedarkkaiser.redmagic.ui.CardStyle
import com.elitedarkkaiser.redmagic.ui.ThemePrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class FreshInstallDefaultsTest {
    private lateinit var context: Context

    @Before fun resetPreferences() {
        context = RuntimeEnvironment.getApplication()
        listOf(
            "appearance", AppPrefs.PREFS_NAME, "triggers", "root_access_cache",
            "perf_tweaks", "fresh_install_defaults"
        ).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        CardStyle.load(context)
    }

    @Test fun freshAppearanceMatchesRequestedDefaults() {
        assertTrue(ThemePrefs.useDynamicColor(context))
        assertTrue(ThemePrefs.useDockedBar(context))
        assertEquals("CIRCLES", ThemePrefs.backgroundAnimation(context))
        assertEquals(0.25f, CardStyle.blur, 0f)
        assertTrue(CardStyle.connected)
    }

    @Test fun explicitAppearanceValuesWinIncludingZeroBlur() {
        ThemePrefs.setUseDynamicColor(context, false)
        ThemePrefs.setUseDockedBar(context, false)
        ThemePrefs.setBackgroundAnimation(context, "NONE")
        CardStyle.setBlur(context, 0f)
        CardStyle.setConnected(context, false)
        CardStyle.load(context)
        assertFalse(ThemePrefs.useDynamicColor(context))
        assertFalse(ThemePrefs.useDockedBar(context))
        assertEquals("NONE", ThemePrefs.backgroundAnimation(context))
        assertEquals(0f, CardStyle.blur, 0f)
        assertFalse(CardStyle.connected)
    }

    @Test fun legacyTransparencyStillMigratesInsteadOfTakingTheNewDefault() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putFloat("card_alpha", 0.8f).commit()
        CardStyle.load(context)
        assertEquals(0.2f, CardStyle.blur, 0.0001f)
        CardStyle.setBlur(context, 0.6f)
        CardStyle.load(context)
        assertEquals(0.6f, CardStyle.blur, 0f)
    }

    @Test fun allStoredHardwareTogglesStartOffAndSavedValuesRemainOn() {
        val trigger = readTriggerPrefsSnapshot(context)
        assertFalse(trigger.triggerEnabled)
        assertFalse(trigger.hapticsEnabled)
        assertFalse(trigger.intentUnlockRightTrigger)
        assertFalse(trigger.triggersAutoStart)
        assertFalse(isFanPowerEnabledStorage(context))
        assertFalse(isAutoFanEnabledStorage(context))
        assertFalse(MasterSwitches.gameModeEnabled(context))
        assertFalse(savedFanLedStateStorage(context).enabled)
        assertFalse(savedLogoLedStateStorage(context).enabled)
        assertFalse(savedShoulderLedStateStorage(context).enabled)
        assertFalse(savedPumpStateStorage(context).enabled)
        assertFalse(savedPumpStateStorage(context).autoEnabled)
        assertFalse(CallLightingState.isEnabled(context))
        assertFalse(CallLightingState.shouldPauseFanDuringCalls(context))
        assertFalse(ChargingLedState.isEnabled(context))
        context.getSharedPreferences("triggers", Context.MODE_PRIVATE).edit()
            .putBoolean("haptics_enabled", true).putBoolean("intent_unlock_right_trigger", true)
            .commit()
        saveFanPowerEnabledStorage(context, true)
        assertTrue(readTriggerPrefsSnapshot(context).hapticsEnabled)
        assertTrue(readTriggerPrefsSnapshot(context).intentUnlockRightTrigger)
        assertTrue(isFanPowerEnabledStorage(context))
    }

    @Test fun performanceOptimizerStartsOffAndHonorsSavedChoice() {
        assertFalse(PerfTweaksPrefs.getBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER))
        PerfTweaksPrefs.setBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER, true)
        assertTrue(PerfTweaksPrefs.getBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER))
    }

    @Test fun deviceDefaultsWaitForRootAndRunOnlyOnceAcrossLaterLaunches() {
        FreshInstallDefaults.prepare(context)
        // Startup can persist mappings and a denied root result before root is later granted.
        initDefaultTriggerMappingsStorage(context)
        setCachedRootAccessStorage(context, false)
        FreshInstallDefaults.prepare(context)
        val calls = mutableListOf<String>()
        val actions = FreshInstallDefaults.controls.associateWith { key ->
            { calls.add(key); true }
        }
        assertTrue(calls.isEmpty())
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        assertEquals(FreshInstallDefaults.controls, calls.toSet())
        val firstCount = calls.size
        FreshInstallDefaults.prepare(context)
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        assertEquals(firstCount, calls.size)
    }

    @Test fun existingInstallNeverResetsDeviceControls() {
        ThemePrefs.setUseDynamicColor(context, false)
        FreshInstallDefaults.prepare(context)
        var calls = 0
        FreshInstallDefaults.initializeDeviceControls(context,
            FreshInstallDefaults.controls.associateWith { { calls++; true } })
        assertEquals(0, calls)
        assertFalse(ThemePrefs.useDynamicColor(context))
    }

    @Test fun failedInitializationRetriesOnlyTheFailedControl() {
        FreshInstallDefaults.prepare(context)
        val calls = mutableMapOf<String, Int>()
        var allowMagicKey = false
        val actions = FreshInstallDefaults.controls.associateWith { key ->
            {
                calls[key] = (calls[key] ?: 0) + 1
                key != "magic_key" || allowMagicKey
            }
        }
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        allowMagicKey = true
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        FreshInstallDefaults.controls.forEach { key ->
            assertEquals(if (key == "magic_key") 2 else 1, calls[key])
        }
    }

    @Test fun userChoiceCancelsAnOutstandingFailedReset() {
        FreshInstallDefaults.prepare(context)
        var calls = 0
        val actions = FreshInstallDefaults.controls.associateWith {
            { calls++; false }
        }
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        val initialCalls = calls
        FreshInstallDefaults.keepUserChoice(context, *FreshInstallDefaults.controls.toTypedArray())
        FreshInstallDefaults.initializeDeviceControls(context, actions)
        assertEquals(initialCalls, calls)
    }
}

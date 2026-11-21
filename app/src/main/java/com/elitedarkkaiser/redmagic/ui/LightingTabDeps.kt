package com.elitedarkkaiser.redmagic.ui

import android.view.View
import android.widget.LinearLayout

data class LightingTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    val getFanLedEnabled: () -> Boolean,
    val setFanLedEnabled: (Boolean) -> Unit,
    val getLogoLedEnabled: () -> Boolean,
    val setLogoLedEnabled: (Boolean) -> Unit,
    val showFanLedDialog: () -> Unit,
    val showLogoLedDialog: () -> Unit,
    val getGameModeEnabled: () -> Boolean,
    val setGameModeEnabled: (Boolean) -> Unit,
    val showGameModeAppPicker: () -> Unit,
    val gameModeEditor: () -> View,
    val gameModeAppsSummary: () -> String,
    val getChargingLedEnabled: () -> Boolean,
    val setChargingLedEnabled: (Boolean) -> Unit,
    val chargingEditor: () -> View,
    val getCallLightingEnabled: () -> Boolean,
    val setCallLightingEnabled: (Boolean) -> Unit,
    val getPauseFanDuringCalls: () -> Boolean,
    val setPauseFanDuringCalls: (Boolean) -> Unit,
    val callLightingEditor: () -> View
)

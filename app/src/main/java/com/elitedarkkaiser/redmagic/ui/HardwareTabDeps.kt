package com.elitedarkkaiser.redmagic.ui

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

data class HardwareTabDeps(
    val scrollTabContainer: () -> LinearLayout,

    val showTriggerSetupDialog: (onChanged: () -> Unit) -> Unit,
    val enableTriggersAndService: () -> Unit,
    val disableTriggersAndService: () -> Unit,
    val isTriggersEnabled: () -> Boolean,
    val testHaptic: () -> Unit,

    // Magic Key (merged in from the old Controls tab).
    val readMagicKeyModeLabel: () -> String,
    val applyMagicKeyMode: (String, TextView, Button?) -> Unit,
    val disableMagicKeyMode: (TextView, Button?) -> Unit,
    val resolveMagicKeyAppLabel: (String?) -> String,
    val savedMagicKeyAppPackage: () -> String?,
    val showMagicKeyAppPicker: (Button) -> Unit,
    val magicKeyOptions: (() -> android.view.View)? = null
)


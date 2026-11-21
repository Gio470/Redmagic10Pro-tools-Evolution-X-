package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.os.Build
import com.elitedarkkaiser.redmagic.BuildConfig
import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.MagicKeyRuntime
import com.elitedarkkaiser.redmagic.gametrigger.GameTrigger
import com.elitedarkkaiser.redmagic.gametrigger.TriggerSetup
import com.elitedarkkaiser.redmagic.parts.CoolingOverrides
import com.elitedarkkaiser.redmagic.parts.PartsPrefs
import com.elitedarkkaiser.redmagic.parts.PartsRuntime

/** Everything Settings › Diagnostics copies, in one plain-text report. */
internal object DiagnosticsReport {
    fun build(context: Context): String {
        val trigger = TriggerSetup.state(context)
        return buildString {
            appendLine("Redmagic Tools ${BuildConfig.VERSION_NAME}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Firmware: ${Build.DISPLAY}")

            section("Game trigger mapping")
            appendLine("Permissions: ${TriggerSetup.lastMessage}; overlay=${trigger.overlay}; " +
                "accessibility=${trigger.accessibility}")
            appendLine(GameTrigger.diagnostics(context))

            section("Magic Key")
            appendLine("Action: ${MagicKeyRuntime.mode(context).label}")
            appendLine("Reversed: ${MagicKeyRuntime.reversed(context)}")
            appendLine("Input: ${MagicKeyRuntime.input}")
            appendLine("Status: ${MagicKeyRuntime.status}")

            section("Device controls")
            appendLine("Selected profile: ${PartsPrefs.profile(context).label}")
            appendLine("Effective: ${PartsRuntime.performance}")
            appendLine("Ambient: ${PartsRuntime.ambient}")
            appendLine("Ambient detail: ${PartsRuntime.ambientDebug}")
            appendLine("Requested: tap=${PartsPrefs.bool(context, PartsPrefs.TAP)} lift=${PartsPrefs.bool(context, PartsPrefs.LIFT)} " +
                "schedule=${PartsPrefs.bool(context, PartsPrefs.SCHEDULE)} separation=${PartsPrefs.separation(context).key}")
            appendLine("Schedule: ${PartsRuntime.scheduleDetail}")
            appendLine("Helper: ${PartsRuntime.helperDetail}")
            appendLine("Charging: ${PartsRuntime.charge}")
            appendLine("Separation: ${PartsRuntime.chargeDetail}")
            appendLine("Temperatures: ${PartsRuntime.temperatures}")
            appendLine("Fan minimum: ${CoolingOverrides.floor}")
            appendLine("Status: ${PartsRuntime.status}")

            section("Hardware writes")
            append(HardwareController.writeLogSnapshot())
        }
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("== $title ==")
    }
}

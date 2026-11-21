package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import android.widget.Toast

internal object GameModeActions {

    fun startServiceSilentlyIfPermitted(context: Context) {
        // Several services restart this one after they finish with the LEDs. The master switch on
        // Lighting has to hold across all of them, or switching Game Mode off would last only until
        // the next call or charge ended.
        if (!MasterSwitches.gameModeEnabled(context)) return
        if (PermissionActions.hasUsageStatsPermission(context)) {
            context.startService(Intent(context, GameModeService::class.java))
        }
    }

    fun startServiceIfPermitted(context: Context) {
        if (!PermissionActions.hasUsageStatsPermission(context)) {
            Toast.makeText(
                context,
                "Grant Usage Access to enable Game Mode",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        context.startService(Intent(context, GameModeService::class.java))
    }

}


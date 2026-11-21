package com.elitedarkkaiser.redmagic

import android.widget.Button
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.AppPicker

internal object MagicKeyAppPickerDialog {
    fun show(activity: MainActivity, targetButton: Button, statusLabel: TextView?,
        applyLaunchAppMagicKeyMode: (String, String, TextView, Button) -> Unit) {
        val status = statusLabel ?: return
        AppPicker.show(activity,
            title = "Launch an app",
            supporting = "Pick the app the Magic Key opens.",
            mode = AppPicker.Mode.Single(savedMagicKeyAppPackageStorage(activity)) { app ->
                applyLaunchAppMagicKeyMode(app.packageName, app.label, status, targetButton)
            })
    }
}

package com.elitedarkkaiser.redmagic

object MagicKeyActions {
    fun readModeLabel(context: android.content.Context): String = MagicKeyRuntime.readMode(context).label

    fun resolveAppLabel(context: android.content.Context, pkg: String?): String {
        if (pkg.isNullOrBlank()) return "Choose App"
        return try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Throwable) {
            pkg
        }
    }

    fun applyMode(
        activity: android.app.Activity,
        label: String,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button? = null,
        refreshStatus: () -> Unit
    ) {
        val ok = MagicKeyRuntime.configure(activity, MagicKeyMode.fromLabel(label))
        if (ok) {
            saveMagicKeyAppPackageStorage(activity, null)
            sliderButton?.text = "MAGIC KEY APP: Choose App"
            statusLabel.text = "Current: $label"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key set to $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to set Magic Key to $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun applyLaunchAppMode(
        activity: android.app.Activity,
        pkg: String,
        label: String,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button,
        refreshStatus: () -> Unit
    ) {
        saveMagicKeyAppPackageStorage(activity, pkg)
        val ok = MagicKeyRuntime.configure(activity, MagicKeyMode.APP)
        if (ok) {
            saveMagicKeyAppPackageStorage(activity, pkg)
            sliderButton.text = "MAGIC KEY APP: $label"
            statusLabel.text = "Current: Launch App"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key set to launch $label",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to set Magic Key app",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun disableMode(
        activity: android.app.Activity,
        statusLabel: android.widget.TextView,
        sliderButton: android.widget.Button? = null,
        refreshStatus: () -> Unit
    ) {
        val ok = MagicKeyRuntime.configure(activity, MagicKeyMode.DISABLED)
        if (ok) {
            sliderButton?.text = "MAGIC KEY APP: Choose App"
            statusLabel.text = "Current: Disabled"
            refreshStatus()
            android.widget.Toast.makeText(
                activity,
                "Magic Key disabled",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                activity,
                "Failed to disable Magic Key",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

}


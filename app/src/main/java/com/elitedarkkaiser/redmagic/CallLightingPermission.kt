package com.elitedarkkaiser.redmagic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** READ_PHONE_STATE, which call lighting cannot work without. */
object CallLightingPermission {

    const val PERMISSION: String = Manifest.permission.READ_PHONE_STATE

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Grants it with `pm grant`. Blocking -- call it off the main thread. Returns whether the
     * permission is held afterwards, which is what the caller actually needs to know.
     */
    fun grantWithRoot(context: Context): Boolean {
        if (granted(context)) return true
        RootShell.exec("pm grant ${context.packageName} $PERMISSION")
        return granted(context)
    }
}

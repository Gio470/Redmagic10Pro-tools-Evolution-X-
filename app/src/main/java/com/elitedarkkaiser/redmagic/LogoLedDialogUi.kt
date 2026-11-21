package com.elitedarkkaiser.redmagic

import android.app.Activity
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.Icons
import com.elitedarkkaiser.redmagic.ui.LedZoneEditor
import com.elitedarkkaiser.redmagic.ui.M3Dialog

internal object LogoLedDialogUi {
    fun show(activity: Activity, current: LedState, onChanged: (LedState) -> Unit, onClosed: () -> Unit) {
        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, "Logo LED", "Choose an animation and colour. Changes apply automatically.", Icons.LOGO))
            addView(LedZoneEditor.create(activity, "Light", current, allowOff = false, onChanged = onChanged))
        }
        M3Dialog.show(activity, M3Dialog.scroll(activity, panel)).setOnDismissListener { onClosed() }
    }
}

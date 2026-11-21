package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.content.Context
import com.elitedarkkaiser.redmagic.ui.AppFilter
import com.elitedarkkaiser.redmagic.ui.AppPicker

/** Chooses the saved games used by Game Mode and the fan's "Turn on in games". */
fun showGamePickerDialogUI(context: Context, onChanged: (Set<String>) -> Unit) {
    val activity = context as? Activity ?: return
    AppPicker.show(activity,
        title = "Your games",
        supporting = "Game Mode and the fan treat these as games. Changes save as you tap.",
        mode = AppPicker.Mode.Multi(getSavedGamePackagesStorage(context)) { selected ->
            setSavedGamePackagesStorage(context, selected)
            onChanged(selected)
        },
        filters = listOf(AppFilter.GAMES, AppFilter.ALL, AppFilter.SELECTED))
}

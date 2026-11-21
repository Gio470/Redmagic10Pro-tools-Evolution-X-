package com.elitedarkkaiser.redmagic

import android.content.Context
import com.elitedarkkaiser.redmagic.storage.AppPrefs

/**
 * The two master switches that had no state of their own before the section headers grew one.
 *
 * Most masters were already there in some form -- triggers, haptics, call lighting and charging
 * each had an "Enable X" row that simply moved up to the header, and the Magic Key's is the device
 * mode itself. These two are new: LED zones had no way to say "all off", and Game Mode's service
 * was started implicitly rather than switched on.
 */
object MasterSwitches {

    private fun prefs(context: Context) =
        context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    // ---- Game Mode -----------------------------------------------------------------------

    /** Fresh installs leave game-mode automation off until the user enables it. */
    fun gameModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(AppPrefs.GAME_MODE_MASTER_ENABLED, false)

    fun setGameModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AppPrefs.GAME_MODE_MASTER_ENABLED, enabled).apply()
        if (enabled) {
            GameModeActions.startServiceIfPermitted(context)
        } else {
            context.stopService(android.content.Intent(context, GameModeService::class.java))
        }
    }

    // ---- Magic Key -----------------------------------------------------------------------

    /**
     * The action to go back to when the Magic Key's master switch is turned on again.
     *
     * The device has one setting for both "what the key does" and "the key does nothing", so
     * switching off is destructive -- without remembering the last real action, switching back on
     * would have nothing to restore and the user would have to pick again.
     */
    fun lastMagicKeyMode(context: Context): String =
        prefs(context).getString(AppPrefs.MAGIC_KEY_LAST_MODE, null) ?: "Camera"

    fun setLastMagicKeyMode(context: Context, mode: String) {
        if (mode.isBlank() || mode == "Disabled" || mode == "Unknown") return
        prefs(context).edit().putString(AppPrefs.MAGIC_KEY_LAST_MODE, mode).apply()
    }
}


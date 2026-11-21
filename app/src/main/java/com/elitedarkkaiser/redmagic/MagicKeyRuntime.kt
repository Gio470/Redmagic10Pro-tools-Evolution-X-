package com.elitedarkkaiser.redmagic

import android.content.Context
import android.content.Intent
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import java.util.concurrent.TimeUnit

internal object MagicKeyRuntime {
    private const val MODE = "magic_key_root_mode"
    private const val REVERSE = "magic_key_reverse"
    @Volatile var status = "Not running"
    @Volatile var input = ""
    private fun prefs(context: Context) = context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    fun reversed(context: Context) = prefs(context).getBoolean(REVERSE, false)
    fun mode(context: Context) = MagicKeyMode.fromValue(prefs(context).getInt(MODE, 0))
    fun wanted(context: Context) = mode(context) != MagicKeyMode.DISABLED

    fun readMode(context: Context): MagicKeyMode {
        sync(context)
        return mode(context)
    }

    fun setReversed(context: Context, reversed: Boolean) {
        prefs(context).edit().putBoolean(REVERSE, reversed).apply()
        sync(context)
    }

    fun configure(context: Context, mode: MagicKeyMode): Boolean {
        prefs(context).edit().putInt(MODE, mode.value).apply()
        if (mode != MagicKeyMode.DISABLED) MasterSwitches.setLastMagicKeyMode(context, mode.label)
        com.elitedarkkaiser.redmagic.parts.PartsRuntime.magicOn = false
        com.elitedarkkaiser.redmagic.parts.PartsRuntime.sync(context)
        return sync(context)
    }

    fun sync(context: Context): Boolean = runCatching {
        val intent = Intent(context, MagicKeyService::class.java)
        if (wanted(context)) context.startForegroundService(intent) else context.stopService(intent)
        true
    }.getOrElse { status = "Could not start: ${it.message}"; false }

    /** Bounded root calls, with both streams drained, for discovery and action commands. */
    fun root(command: String): String? {
        val child = runCatching { ProcessBuilder("su", "-c", command).redirectErrorStream(true).start() }.getOrNull() ?: return null
        val output = StringBuilder()
        val reader = Thread({ runCatching { child.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line -> if (output.length < 65_536) output.append(line).append('\n') }
        } } }, "magic-key-output").apply { isDaemon = true; start() }
        return try {
            if (!child.waitFor(12, TimeUnit.SECONDS)) { child.destroyForcibly(); null }
            else { reader.join(500); if (child.exitValue() == 0) output.toString().trim() else null }
        } finally { if (child.isAlive) child.destroyForcibly() }
    }
}

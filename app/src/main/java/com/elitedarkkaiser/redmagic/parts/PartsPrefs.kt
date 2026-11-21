package com.elitedarkkaiser.redmagic.parts

import android.content.Context
import android.content.Intent
import com.elitedarkkaiser.redmagic.MagicKeyMode
import com.elitedarkkaiser.redmagic.MagicKeyRuntime

internal object PartsPrefs {
    const val NAME = "nubia_parts"
    const val PROFILE = "performance_profile"
    const val SEPARATION = "charge_separation"
    const val TAP = "aod_tap"
    const val LIFT = "aod_lift"
    const val SCHEDULE = "aod_schedule"
    const val START = "aod_schedule_start"
    const val END = "aod_schedule_end"
    const val SUSPENDED = "aod_schedule_suspended"
    const val GAMES = "fan_games"
    const val FAST = "fan_fast_charging"
    const val FAN_LEVEL = "fan_automation_level"
    fun prefs(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    fun bool(c: Context, key: String) = prefs(c).getBoolean(key, false)
    fun minutes(c: Context, key: String) = prefs(c).getInt(key, if (key == END) 420 else 0).coerceIn(0, 1439)
    fun profile(c: Context) = PerformanceProfile.parse(prefs(c).getString(PROFILE, null))
    fun separation(c: Context) = SeparationMode.parse(prefs(c).getString(SEPARATION, null))
    fun fanLevel(c: Context) = prefs(c).getInt(FAN_LEVEL, 3).coerceIn(1, 5)
    fun set(c: Context, key: String, value: Boolean) { prefs(c).edit().putBoolean(key, value).apply(); PartsRuntime.sync(c) }
    fun set(c: Context, key: String, value: String) { prefs(c).edit().putString(key, value).apply(); PartsRuntime.sync(c) }
    fun set(c: Context, key: String, value: Int) { prefs(c).edit().putInt(key, value).apply(); PartsRuntime.sync(c) }
    fun wanted(c: Context) = profile(c) != PerformanceProfile.BALANCED || separation(c) != SeparationMode.OFF ||
        listOf(TAP, LIFT, SCHEDULE, SUSPENDED, GAMES, FAST, "fan_override_active", "cleanup_pending").any { bool(c, it) } || MagicKeyRuntime.mode(c) == MagicKeyMode.PERFORMANCE
}

internal object PartsRuntime {
    @Volatile var status = "Off"
    @Volatile var performance = "Balanced"
    @Volatile var ambient = "Off"
    @Volatile var charge = "Charging normally"
    @Volatile var skinC: Double? = null
    @Volatile var temperatures = "Waiting for battery readings"
    @Volatile var separated = false
    @Volatile var chargeDetail = "Not requested"
    @Volatile var ambientDebug = "Helper has not reported"
    @Volatile var helperDetail = "Helper has not reported"
    @Volatile var scheduleDetail = "Off"
    @Volatile var magicOn = false
    @Volatile var actualProfile = PerformanceProfile.BALANCED
    fun sync(c: Context): Boolean = runCatching {
        if (PartsPrefs.wanted(c)) {
            check(android.os.Build.MODEL.equals("NX789J", true) || android.os.Build.DEVICE.equals("NX789J", true)) {
                "These device controls require a RedMagic 10 Pro (NX789J)"
            }
            c.startForegroundService(Intent(c, PartsService::class.java))
        }
        else {
            c.stopService(Intent(c, PartsService::class.java))
            val alarm = android.app.PendingIntent.getBroadcast(c, 0, Intent(c, PartsAlarmReceiver::class.java),
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
            if (alarm != null) c.getSystemService(android.app.AlarmManager::class.java).cancel(alarm)
        }
        true
    }.getOrElse { status = it.message ?: "Could not start device controls"; false }
    fun magic(c: Context, on: Boolean) { magicOn = on; sync(c) }
}

/* SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 * Scheduling, charging and cooling policy adapted from NubiaParts. */
package com.elitedarkkaiser.redmagic.parts

import android.app.*
import android.content.*
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import com.elitedarkkaiser.redmagic.*
import com.elitedarkkaiser.redmagic.gametrigger.TriggerForeground
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import org.json.JSONObject
import java.util.Calendar

class PartsService : android.app.Service() {
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private lateinit var root: PartsClient
    private lateinit var foreground: TriggerForeground
    private val guards = PartsPolicy.Guards()
    private val separation = PartsPolicy.Separation()
    private val gameHold = PartsPolicy.GameHold()
    private var battery: Intent? = null
    private var nativeGames = emptySet<String>()
    private var cachedAod = 1
    private var aodReadAt = 0L
    private var nextAlarm = 0L
    @Volatile private var alive = true
    private val preferences = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (::worker.isInitialized && alive) worker.post { evaluate() }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            worker.post {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
                if (intent.action == Intent.ACTION_POWER_DISCONNECTED) battery = Intent(battery ?: Intent()).putExtra(BatteryManager.EXTRA_PLUGGED, 0)
                if (intent.action == Intent.ACTION_POWER_CONNECTED && (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) == 0)
                    battery = Intent(battery ?: Intent()).putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
                if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) nextAlarm = 0
                evaluate()
            }
        }
    }
    private lateinit var observer: ContentObserver
    private val tick = object : Runnable {
        override fun run() { if (alive) { evaluate(); worker.postDelayed(this, 10_000) } }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Device controls", NotificationManager.IMPORTANCE_LOW))
        startForeground(246, notification())
        thread = HandlerThread("device-controls").apply { start() }
        worker = Handler(thread.looper)
        root = PartsClient(this, worker) { result ->
            result.optJSONArray("games")?.let { list -> nativeGames = (0 until list.length()).map { list.getString(it) }.toSet() }
            if (result.has("aod")) { cachedAod = result.getInt("aod"); aodReadAt = SystemClock.elapsedRealtime() }
            if (CoolingOverrides.floor > 0 && result.has("fanLevel") && result.getInt("fanLevel") < CoolingOverrides.floor)
                if (CoolingOverrides.reapply()) root.invalidate()
            PartsRuntime.actualProfile = PerformanceProfile.parse(result.optString("profile"))
            PartsRuntime.separated = result.optBoolean("separated")
            PartsRuntime.chargeDetail = result.optString("chargeDetail", PartsRuntime.chargeDetail)
            PartsRuntime.ambientDebug = result.optString("ambientDebug", "Not reported by this helper")
            PartsRuntime.helperDetail = "applied ${result.optString("requested", "?")} · state in ${result.optString("state", "?")}" +
                " · startup recovery: ${result.optString("recovery", "?")}"
            PartsRuntime.skinC = result.optDouble("skin", Double.NaN).takeIf { it.isFinite() }
            PartsRuntime.ambient = result.optString("ambient", "Off")
            PartsRuntime.status = result.optString("error").ifEmpty { "Running" }
            if (result.optString("error").isEmpty() && PartsPrefs.bool(this, "cleanup_pending"))
                PartsPrefs.prefs(this).edit().putBoolean("cleanup_pending", false).apply()
            evaluate()
        }
        foreground = TriggerForeground(worker) { evaluate() }
        observer = object : ContentObserver(worker) { override fun onChange(selfChange: Boolean) { evaluate() } }
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("gamespace_game_list"), false, observer) }
        runCatching { contentResolver.registerContentObserver(Settings.Secure.getUriFor("doze_always_on"), false, observer) }
        PartsPrefs.prefs(this).registerOnSharedPreferenceChangeListener(preferences)
        getSharedPreferences(AppPrefs.PREFS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(preferences)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        battery = registerReceiver(receiver, filter)
        worker.post { CoolingOverrides.recover(this); worker.post(tick) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        worker.post { evaluate() }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun evaluate() {
        if (!alive) return
        runCatching { evaluateState() }.onFailure { PartsRuntime.status = it.message ?: "Device control update failed" }
    }
    private fun evaluateState() {
        if (!alive) return
        val info = battery
        val plugged = (info?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = info?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING
        val current = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }
        val level = info?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
        val scale = info?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val percent = level?.let { it * 100 / scale }
        val temperature = info?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val power = PartsPolicy.chargingPower(plugged, charging,
            info?.getIntExtra("max_charging_current", 0)?.toLong() ?: 0,
            info?.getIntExtra("max_charging_voltage", 0)?.toLong() ?: 0,
            current?.toLong() ?: 0, info?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toLong() ?: 0)
        guards.update(temperature, percent, power, plugged)
        val screen = getSystemService(PowerManager::class.java).isInteractive
        val needsGames = PartsPrefs.bool(this, PartsPrefs.GAMES) || PartsPrefs.separation(this) == SeparationMode.GAMING
        if (needsGames && screen) foreground.start() else { foreground.stop() }
        val publicGames = runCatching { Settings.System.getString(contentResolver, "gamespace_game_list") }.getOrNull()
        val games = PartsPolicy.games(publicGames) + nativeGames + getSavedGamePackagesStorage(this)
        val game = gameHold.update(SystemClock.elapsedRealtime(),
            foreground.current()?.let { it in games } == true, screen)
        val bound = MagicKeyRuntime.mode(this) == MagicKeyMode.PERFORMANCE
        val profile = guards.profile(PartsPrefs.profile(this), bound, PartsRuntime.magicOn)
        val pause = CallLightingState.isActive(this) && CallLightingState.shouldPauseFanDuringCalls(this)
        val floor = PartsPolicy.fanFloor(screen, game, PartsPrefs.bool(this, PartsPrefs.GAMES), guards.fast,
            PartsPrefs.bool(this, PartsPrefs.FAST), profile, PartsPrefs.fanLevel(this), pause)
        CoolingOverrides.setFloor(this, floor)
        val separate = separation.wanted(SystemClock.elapsedRealtime(), plugged, guards.low, PartsPrefs.separation(this),
            game, PartsRuntime.actualProfile, PartsRuntime.separated, current?.let { it / 1000 })
        evaluateSchedule()
        val aod = runCatching { Settings.Secure.getInt(contentResolver, "doze_always_on", 1) != 0 }.getOrDefault(true)
        root.send(JSONObject().put("profile", profile.key).put("screen", screen).put("separate", separate)
            .put("percent", percent ?: JSONObject.NULL).put("batteryTemp", temperature ?: JSONObject.NULL).put("tap", PartsPrefs.bool(this, PartsPrefs.TAP)).put("lift", PartsPrefs.bool(this, PartsPrefs.LIFT)).put("aod", aod))
        PartsRuntime.performance = when {
            guards.hot && PartsPrefs.profile(this) != PerformanceProfile.BALANCED -> "Battery hot or unavailable · Balanced limits"
            bound && !PartsRuntime.magicOn -> "Balanced · waiting for Magic Key"
            else -> PartsRuntime.actualProfile.label + if (!screen && profile != PerformanceProfile.BALANCED) " · clock floors restored while screen off" else ""
        }
        PartsRuntime.temperatures = "Skin ${PartsRuntime.skinC?.let { "%.1f °C".format(java.util.Locale.US, it) } ?: "unavailable"} · Battery ${temperature?.let { "%.1f °C".format(java.util.Locale.US, it / 10f) } ?: "unavailable"}" +
            " · ${percent?.let { "$it%" } ?: "level unavailable"}"
        PartsRuntime.charge = if (PartsRuntime.separated) "Running from charger · battery idle"
            else if (separate) "Enabling charge separation" else if (plugged && !guards.low && PartsPrefs.separation(this) != SeparationMode.OFF) "Charging normally · waiting for conditions" else "Charging normally"
        if (current != null) PartsRuntime.charge += " · ${current / 1000} mA"
        if (!PartsPrefs.wanted(this)) stopSelf()
    }
    private fun readAod(): Int {
        val direct = runCatching { Settings.Secure.getInt(contentResolver, "doze_always_on", -1) }.getOrDefault(-1)
        if (direct in 0..1) { cachedAod = direct; return direct }
        val now = SystemClock.elapsedRealtime()
        if (now - aodReadAt >= 10_000 || aodReadAt == 0L) {
            val rootValue = MagicKeyRuntime.root("settings --user current get secure doze_always_on")?.trim()?.toIntOrNull()
            if (rootValue in 0..1) cachedAod = rootValue!!
            aodReadAt = now
        }
        return cachedAod
    }
    private fun evaluateSchedule() {
        val prefs = PartsPrefs.prefs(this)
        val enabled = PartsPrefs.bool(this, PartsPrefs.SCHEDULE)
        val start = PartsPrefs.minutes(this, PartsPrefs.START); val end = PartsPrefs.minutes(this, PartsPrefs.END)
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val inside = enabled && PartsPolicy.inWindow(minute, start, end)
        val owned = prefs.getBoolean(PartsPrefs.SUSPENDED, false)
        val aod = if (enabled || owned) readAod() != 0 else true
        val action = PartsPolicy.aodAction(enabled, minute, start, end, owned, aod)
        PartsRuntime.scheduleDetail = if (!enabled) "Off" else
            "${if (inside) "inside" else "outside"} the window ${start / 60}:%02d-${end / 60}:%02d, AOD ${if (aod) "on" else "off"}".format(start % 60, end % 60)
        if (action != null) {
            val out = MagicKeyRuntime.root("settings --user current put secure doze_always_on $action")
            if (MagicKeyCommands.succeeded(out)) prefs.edit().putBoolean(PartsPrefs.SUSPENDED, action == 0).apply()
            else {
                PartsRuntime.status = "Could not change the AOD schedule"
                PartsRuntime.scheduleDetail += " · setting doze_always_on=$action failed: ${out?.trim() ?: "no root"}"
            }
            if (out != null) { cachedAod = action; aodReadAt = SystemClock.elapsedRealtime() }
        }
        val pending = PendingIntent.getBroadcast(this, 0, Intent(this, PartsAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = getSystemService(AlarmManager::class.java)
        if (!enabled || start == end) { alarms.cancel(pending); nextAlarm = 0; return }
        val target = if (inside) end else start
        val at = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, target / 60); set(Calendar.MINUTE, target % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        if (at == nextAlarm) return
        nextAlarm = at
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 246, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Device controls").setContentText("Performance, ambient display and cooling automation")
            .setOngoing(true).setContentIntent(open).build()
    }
    override fun onDestroy() {
        alive = false
        runCatching { unregisterReceiver(receiver) }
        runCatching { contentResolver.unregisterContentObserver(observer) }
        PartsPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(preferences)
        getSharedPreferences(AppPrefs.PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferences)
        worker.removeCallbacksAndMessages(null)
        worker.post {
            foreground.stop(); val failure = root.close(true); CoolingOverrides.setFloor(this, 0)
            PartsRuntime.actualProfile = PerformanceProfile.BALANCED
            PartsRuntime.performance = "Balanced"; PartsRuntime.ambient = "Off"; PartsRuntime.charge = "Charging normally"
            PartsRuntime.separated = false; PartsRuntime.status = failure ?: "Off"
            if (failure != null) PartsPrefs.prefs(this).edit().putBoolean("cleanup_pending", true).apply()
            thread.quitSafely()
        }
        super.onDestroy()
    }
    companion object { private const val CHANNEL = "device_parts" }
}

class PartsAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) { PartsRuntime.sync(context) }
}

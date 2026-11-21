package com.elitedarkkaiser.redmagic

import android.content.Context

private const val GAME_PREFS_NAME = "redmagic_hw_controls_prefs"
private const val GAME_MODE_PACKAGES_KEY = "game_mode_packages"
private const val GAME_MODE_LED_OVERRIDE_ACTIVE_KEY = "game_mode_led_override_active"

private const val GAME_MODE_FAN_ENABLED_KEY = "game_mode_fan_enabled"
private const val GAME_MODE_FAN_LEVEL_KEY = "game_mode_fan_level"
private const val GAME_MODE_PUMP_ENABLED_KEY = "game_mode_pump_enabled"
private const val GAME_MODE_PUMP_PROFILE_KEY = "game_mode_pump_profile"
private const val GAME_MODE_FAN_LED_ENABLED_KEY = "game_mode_fan_led_enabled"
private const val GAME_MODE_FAN_LED_EFFECT_KEY = "game_mode_fan_led_effect"
private const val GAME_MODE_FAN_LED_COLOR_KEY = "game_mode_fan_led_color"
private const val GAME_MODE_LOGO_LED_ENABLED_KEY = "game_mode_logo_led_enabled"
private const val GAME_MODE_LOGO_LED_EFFECT_KEY = "game_mode_logo_led_effect"
private const val GAME_MODE_LOGO_LED_COLOR_KEY = "game_mode_logo_led_color"
private const val GAME_MODE_SHOULDER_LED_ENABLED_KEY = "game_mode_shoulder_led_enabled"
private const val GAME_MODE_SHOULDER_LED_EFFECT_KEY = "game_mode_shoulder_led_effect"
private const val GAME_MODE_SHOULDER_LED_COLOR_KEY = "game_mode_shoulder_led_color"

fun getSavedGameModeProfileStorage(context: Context): GameModeProfile {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    return GameModeProfile(
        fanEnabled = prefs.getBoolean(GAME_MODE_FAN_ENABLED_KEY, true),
        fanLevel = prefs.getInt(GAME_MODE_FAN_LEVEL_KEY, 3),
        pumpEnabled = prefs.getBoolean(GAME_MODE_PUMP_ENABLED_KEY, false),
        pumpProfile = prefs.getString(GAME_MODE_PUMP_PROFILE_KEY, "quick") ?: "quick",
        fanLedEnabled = prefs.getBoolean(GAME_MODE_FAN_LED_ENABLED_KEY, true),
        fanLedEffect = prefs.getString(GAME_MODE_FAN_LED_EFFECT_KEY, "steady") ?: "steady",
        fanLedColor = prefs.getInt(GAME_MODE_FAN_LED_COLOR_KEY, 5),
        logoLedEnabled = prefs.getBoolean(GAME_MODE_LOGO_LED_ENABLED_KEY, true),
        logoLedEffect = prefs.getString(GAME_MODE_LOGO_LED_EFFECT_KEY, "steady") ?: "steady",
        logoLedColor = prefs.getInt(GAME_MODE_LOGO_LED_COLOR_KEY, 1),
        shoulderLedEnabled = prefs.getBoolean(GAME_MODE_SHOULDER_LED_ENABLED_KEY, true),
        shoulderLedEffect = prefs.getString(GAME_MODE_SHOULDER_LED_EFFECT_KEY, "breathe") ?: "breathe",
        shoulderLedColor = prefs.getInt(GAME_MODE_SHOULDER_LED_COLOR_KEY, 8)
    )
}

fun saveGameModeProfileStorage(context: Context, profile: GameModeProfile) {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean(GAME_MODE_FAN_ENABLED_KEY, profile.fanEnabled)
        .putInt(GAME_MODE_FAN_LEVEL_KEY, profile.fanLevel)
        .putBoolean(GAME_MODE_PUMP_ENABLED_KEY, profile.pumpEnabled)
        .putString(GAME_MODE_PUMP_PROFILE_KEY, profile.pumpProfile)
        .putBoolean(GAME_MODE_FAN_LED_ENABLED_KEY, profile.fanLedEnabled)
        .putString(GAME_MODE_FAN_LED_EFFECT_KEY, profile.fanLedEffect)
        .putInt(GAME_MODE_FAN_LED_COLOR_KEY, profile.fanLedColor)
        .putBoolean(GAME_MODE_LOGO_LED_ENABLED_KEY, profile.logoLedEnabled)
        .putString(GAME_MODE_LOGO_LED_EFFECT_KEY, profile.logoLedEffect)
        .putInt(GAME_MODE_LOGO_LED_COLOR_KEY, profile.logoLedColor)
        .putBoolean(GAME_MODE_SHOULDER_LED_ENABLED_KEY, profile.shoulderLedEnabled)
        .putString(GAME_MODE_SHOULDER_LED_EFFECT_KEY, profile.shoulderLedEffect)
        .putInt(GAME_MODE_SHOULDER_LED_COLOR_KEY, profile.shoulderLedColor)
        .apply()
}

fun gameModeAppsSummaryStorage(context: Context): String {
    val count = getSavedGamePackagesStorage(context).size
    return when {
        count <= 0 -> "No games selected"
        count == 1 -> "1 game selected"
        else -> "$count games selected"
    }
}

fun getSavedGamePackagesStorage(context: Context): MutableSet<String> {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    return (prefs.getStringSet(GAME_MODE_PACKAGES_KEY, emptySet()) ?: emptySet()).toMutableSet()
}

fun setSavedGamePackagesStorage(context: Context, packages: Set<String>) {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(GAME_MODE_PACKAGES_KEY, packages).apply()
}

fun isGameModeLedOverrideActiveStorage(context: Context): Boolean {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(GAME_MODE_LED_OVERRIDE_ACTIVE_KEY, false)
}

fun setGameModeLedOverrideActiveStorage(context: Context, active: Boolean) {
    val prefs = context.getSharedPreferences(GAME_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(GAME_MODE_LED_OVERRIDE_ACTIVE_KEY, active).apply()
}


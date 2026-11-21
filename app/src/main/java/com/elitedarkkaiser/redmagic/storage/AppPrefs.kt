package com.elitedarkkaiser.redmagic.storage

/** Keys shared by the hardware preferences and services. */
object AppPrefs {
    const val PREFS_NAME = "redmagic_hw_controls_prefs"

    const val SELECTED_CURVE = "selected_curve"
    const val AUTO_FAN_ENABLED = "auto_fan_enabled"
    const val FAN_POWER_ENABLED = "fan_power_enabled"
    const val STATUS_REFRESH_INTERVAL_SECONDS = "status_refresh_interval_seconds"
    const val STATUS_REFRESH_INTERVAL_MS = "status_refresh_interval_ms"
    const val CPU_REFRESH_INTERVAL_MS = "cpu_refresh_interval_ms"
    const val MEMORY_REFRESH_INTERVAL_MS = "memory_refresh_interval_ms"
    const val TEMP_UNIT_FAHRENHEIT = "temp_unit_fahrenheit"

    const val FAN_LED_ENABLED = "fan_led_enabled"
    const val FAN_LED_EFFECT = "fan_led_effect"
    const val FAN_LED_COLOR = "fan_led_color"

    const val LOGO_LED_ENABLED = "logo_led_enabled"
    const val LOGO_LED_EFFECT = "logo_led_effect"
    const val LOGO_LED_COLOR = "logo_led_color"

    const val SHOULDER_LED_ENABLED = "shoulder_led_enabled"
    const val SHOULDER_LED_EFFECT = "shoulder_led_effect"
    const val SHOULDER_LED_COLOR = "shoulder_led_color"

    const val PUMP_ENABLED = "pump_enabled"
    const val PUMP_PROFILE = "pump_profile"
    const val PUMP_EXPERIMENTAL_ACCEPTED = "pump_experimental_accepted"
    const val AUTO_PUMP_ENABLED = "auto_pump_enabled"

    const val MAGIC_KEY_APP_PACKAGE = "magic_key_app_package"
    const val MAGIC_KEY_LAST_MODE = "magic_key_last_mode"

    const val GAME_MODE_MASTER_ENABLED = "game_mode_master_enabled"

}

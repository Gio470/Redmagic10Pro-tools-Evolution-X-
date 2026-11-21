package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.CallLightingState
import com.elitedarkkaiser.redmagic.ChargingLedState
import com.elitedarkkaiser.redmagic.HapticStrength
import com.elitedarkkaiser.redmagic.MasterSwitches
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkStorage
import com.elitedarkkaiser.redmagic.getSavedGamePackagesStorage
import com.elitedarkkaiser.redmagic.isAutoFanEnabledStorage
import com.elitedarkkaiser.redmagic.readTriggerPrefsSnapshot
import com.elitedarkkaiser.redmagic.savedFanLedStateStorage
import com.elitedarkkaiser.redmagic.savedLogoLedStateStorage
import com.elitedarkkaiser.redmagic.savedMagicKeyAppPackageStorage
import com.elitedarkkaiser.redmagic.selectedCurveStorage
import com.elitedarkkaiser.redmagic.state.LedState

/**
 * The fan readings the status poll last made, for the cards: MainActivity's poll writes them, the
 * Hardware tab's Fan card and chips read them. Null until the first reading lands.
 */
object LiveHardware {
    @Volatile var rpm: Int? = null
        private set
    @Volatile var temperature: String? = null
        private set
    @Volatile var fanLevel: Int = 0
        private set
    @Volatile var fanOn: Boolean = false
        private set

    fun update(rpm: Int?, temperature: String?, fanLevel: Int, fanOn: Boolean) {
        if (rpm != null) this.rpm = rpm
        if (temperature != null) this.temperature = temperature
        this.fanLevel = fanLevel
        this.fanOn = fanOn
    }

    fun rpmText(): String? = rpm?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%,d rpm", it) }
}

/**
 * How each Hardware tab feature looks as a card: which group it sits in, its shape, whether it
 * takes a whole row, and what it says it is doing right now.
 */
object HardwareCards {
    private const val MAX_FAN_LEVEL = 5

    fun decorate(activity: Activity, item: IconifyGrid.Item): IconifyGrid.Item = when (item.title) {
        "Fan" -> item.copy(group = "Cooling", order = 0, hero = true, shape = CardShape.COOKIE_12,
            status = { fanStatus(activity) }, extra = { fanExtra(it) },
            master = item.master?.let { DevicePartsUi.fanMaster(activity, it) })

        "Game Mode" -> item.copy(group = "Gaming", order = 10, shape = CardShape.SOFT_BURST, tertiary = true,
            status = {
                when (val n = getSavedGamePackagesStorage(activity).size) {
                    0 -> "No games yet"
                    1 -> "1 game"
                    else -> "$n games"
                }
            })
        "Game trigger mapping" -> item.copy(group = "Gaming", order = 11, shape = CardShape.PENTAGON,
            tertiary = true, status = {
                val ready = NativeTgkStorage.readProfiles(activity).count { it.enabled && it.hasAnyCompleteMapping() }
                when (ready) {
                    0 -> "No games set up"
                    1 -> "1 game ready"
                    else -> "$ready games ready"
                }
            })

        "Magic Key" -> item.copy(group = "Controls", order = 20, shape = CardShape.PILL,
            status = { magicKeyStatus(activity) })
        "Triggers" -> item.copy(group = "Controls", order = 21, shape = CardShape.COOKIE_9,
            status = {
                val prefs = readTriggerPrefsSnapshot(activity)
                "L ${action(prefs.leftTriggerAction)} · R ${action(prefs.rightTriggerAction)}"
            })
        "Haptics" -> item.copy(group = "Controls", order = 22, wide = true, shape = CardShape.SUNNY,
            status = { "On every trigger press" }, extra = { hapticsExtra(it) })

        "Fan LED" -> item.copy(group = "Lighting", order = 30, shape = CardShape.SOFT_BURST,
            status = { LightingTabUi.summary(savedFanLedStateStorage(activity), fan = true) })
        "Logo LED" -> item.copy(group = "Lighting", order = 31, shape = CardShape.FLOWER,
            status = { LightingTabUi.summary(savedLogoLedStateStorage(activity), fan = false) })
        "Call lighting" -> item.copy(group = "Lighting", order = 32, shape = CardShape.COOKIE_4,
            status = { callStatus(activity) })
        "Charging" -> item.copy(group = "Lighting", order = 33, shape = CardShape.CLOVER,
            status = { chargingStatus(activity) })

        "Performance mode" -> item.copy(group = "Performance", order = 39, wide = true, shape = CardShape.SUNNY,
            status = { com.elitedarkkaiser.redmagic.parts.PartsRuntime.performance })
        "Charge separation" -> item.copy(group = "Charging", order = 60, wide = true, shape = CardShape.PILL)

        else -> item.copy(group = "More", order = 90)
    }

    fun additional(activity: Activity): List<IconifyGrid.Item> = DevicePartsUi.hardwareCards(activity)

    /** The Hardware tab's chips after "N of M on": temperature and fan speed. */
    fun chips(): List<IconifyGrid.Chip> = listOf(
        IconifyGrid.Chip(Icons.TEMPERATURE) { LiveHardware.temperature },
        IconifyGrid.Chip(Icons.FAN) { LiveHardware.rpmText() }
    )

    // ---- statuses ----------------------------------------------------------------------------

    private fun fanStatus(context: Context): String =
        if (com.elitedarkkaiser.redmagic.parts.CoolingOverrides.floor > 0) {
            "Automatic cooling · level ${com.elitedarkkaiser.redmagic.parts.CoolingOverrides.floor}"
        } else if (!com.elitedarkkaiser.redmagic.isFanPowerEnabledStorage(context) &&
            (com.elitedarkkaiser.redmagic.parts.PartsPrefs.bool(context, com.elitedarkkaiser.redmagic.parts.PartsPrefs.GAMES) ||
             com.elitedarkkaiser.redmagic.parts.PartsPrefs.bool(context, com.elitedarkkaiser.redmagic.parts.PartsPrefs.FAST))) {
            "Automatic cooling · idle"
        } else if (isAutoFanEnabledStorage(context)) {
            "Automatic · ${selectedCurveStorage(context).replaceFirstChar { it.uppercase() }} curve"
        } else {
            val level = LiveHardware.fanLevel
            if (level > 0) "Level $level of $MAX_FAN_LEVEL" else "Manual"
        }

    private val MAGIC_KEY_MODES = mapOf(
        "Camera" to "Camera", "GameSpace" to "Game Space", "Sound Mode" to "Sound mode",
        "Flashlight" to "Flashlight", "Voice Recorder" to "Voice recorder", "Performance mode" to "Performance mode"
    )

    private fun magicKeyStatus(context: Context): String {
        val mode = MasterSwitches.lastMagicKeyMode(context)
        if (mode == "Launch App") {
            val app = savedMagicKeyAppPackageStorage(context)?.let { pkg ->
                runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                }.getOrNull()
            }
            return if (app != null) "Opens $app" else "Launch an app"
        }
        return MAGIC_KEY_MODES[mode] ?: mode
    }

    private fun action(value: String): String =
        if (value.isBlank() || value == "NONE") "—"
        else value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun firstLit(vararg lights: LedState): String? =
        lights.firstOrNull { it.enabled }?.let { LightingTabUi.summary(it, fan = false) }

    private fun callStatus(context: Context): String {
        val fan = CallLightingState.readLed(context, CallLightingState.INCOMING_FAN_ENABLED_KEY,
            CallLightingState.INCOMING_FAN_EFFECT_KEY, CallLightingState.INCOMING_FAN_COLOR_KEY,
            false, "flashing", 5)
        val logo = CallLightingState.readLed(context, CallLightingState.INCOMING_LOGO_ENABLED_KEY,
            CallLightingState.INCOMING_LOGO_EFFECT_KEY, CallLightingState.INCOMING_LOGO_COLOR_KEY,
            false, "flashing", 1)
        return firstLit(fan, logo)?.let { "Ringing · $it" } ?: "No lights set"
    }

    private fun chargingStatus(context: Context): String {
        fun read(enabled: String, effect: String, color: String, default: Int) =
            ChargingLedState.readProfile(context, enabled, effect, color, true, "steady", default)
                .let { LedState(enabled = it.enabled, effect = it.effect, color = it.color) }
        val fan = read(ChargingLedState.FAN_ENABLED_KEY, ChargingLedState.FAN_EFFECT_KEY,
            ChargingLedState.FAN_COLOR_KEY, 5)
        val logo = read(ChargingLedState.LOGO_ENABLED_KEY, ChargingLedState.LOGO_EFFECT_KEY,
            ChargingLedState.LOGO_COLOR_KEY, 1)
        return firstLit(fan, logo) ?: "No lights set"
    }

    // ---- extras ------------------------------------------------------------------------------

    /** The fan's speed as a wavy bar, the temperature beside it, rpm and level underneath. */
    private fun fanExtra(context: Context): LinearLayout {
        val m3 = M3(context)
        val bar = WavyBar(context)
        val temp = CardExtras.chip(context, "--", glyph = Icons.TEMPERATURE)
        val rpm = CardExtras.caption(context)
        val level = CardExtras.caption(context)
        return object : LinearLayout(context), LiveExtra {
            override fun refresh() {
                val on = LiveHardware.fanOn && LiveHardware.fanLevel > 0
                bar.progress = if (on) LiveHardware.fanLevel / MAX_FAN_LEVEL.toFloat() else 0f
                bar.moving = on
                CardExtras.setChipText(temp, LiveHardware.temperature ?: "--")
                rpm.text = LiveHardware.rpmText() ?: if (on) "Spinning up" else "Fan stopped"
                level.text = if (on) "Level ${LiveHardware.fanLevel} of $MAX_FAN_LEVEL" else ""
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = m3.dp(12) })
                addView(temp)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(rpm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(level)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = m3.dp(4) })
            refresh()
        }
    }

    /** How strong a trigger's buzz is, as a meter. */
    private fun hapticsExtra(context: Context): LinearLayout {
        val m3 = M3(context)
        val meter = CardExtras.Meter(context)
        val label = CardExtras.caption(context)
        return object : LinearLayout(context), LiveExtra {
            override fun refresh() {
                val strength = HapticStrength.get(context)
                meter.lit = ((strength + 5) / 10).coerceIn(0, 10)
                label.text = "Strength ${HapticStrength.label(strength)}"
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(meter)
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = m3.dp(12) })
            refresh()
        }
    }

}

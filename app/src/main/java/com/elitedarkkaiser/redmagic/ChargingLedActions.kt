package com.elitedarkkaiser.redmagic

import com.elitedarkkaiser.redmagic.state.LedState

object ChargingLedActions {

    internal fun editor(activity: android.app.Activity): android.widget.LinearLayout {
        val content = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
        fun read(enabled: String, effect: String, colour: String, default: Int) =
            ChargingLedState.readProfile(activity, enabled, effect, colour, false, "steady", default)
                .let { LedState(it.enabled, it.effect, it.color) }
        var fan = read(ChargingLedState.FAN_ENABLED_KEY, ChargingLedState.FAN_EFFECT_KEY, ChargingLedState.FAN_COLOR_KEY, 5)
        var logo = read(ChargingLedState.LOGO_ENABLED_KEY, ChargingLedState.LOGO_EFFECT_KEY, ChargingLedState.LOGO_COLOR_KEY, 1)
        val preview = LedPreview.bind(content, { ChargingLedState.isEnabled(activity) }) {
            mapOf(LiveLedPreview.Zone.FAN to fan, LiveLedPreview.Zone.LOGO to logo)
        }
        fun save(enabled: String, effect: String, colour: String, state: LedState) {
            ChargingLedState.saveProfile(activity, enabled, effect, colour, state.enabled, state.effect, state.color)
            preview()
            if (ChargingLedState.isEnabled(activity)) HardwareServiceActions.startChargingMode(activity)
        }
        content.addView(com.elitedarkkaiser.redmagic.ui.M3Dialog.body(activity,
            "Lights while plugged in. Charging takes priority over call and game lighting. Changes are saved automatically."))
        content.addView(com.elitedarkkaiser.redmagic.ui.LedZoneEditor.create(activity, "Fan LED", fan, fan = true) {
            fan = it; save(ChargingLedState.FAN_ENABLED_KEY, ChargingLedState.FAN_EFFECT_KEY, ChargingLedState.FAN_COLOR_KEY, it)
        })
        content.addView(com.elitedarkkaiser.redmagic.ui.LedZoneEditor.create(activity, "Logo LED", logo) {
            logo = it; save(ChargingLedState.LOGO_ENABLED_KEY, ChargingLedState.LOGO_EFFECT_KEY, ChargingLedState.LOGO_COLOR_KEY, it)
        })
        return content
    }
}

package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.LedZoneEditor
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.Choice
import com.elitedarkkaiser.redmagic.ui.ChoiceGroup
import com.elitedarkkaiser.redmagic.ui.Icons

internal object CallLightingProfileUi {
    fun editor(activity: Activity): LinearLayout {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val host = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        fun profile(incoming: Boolean): LinearLayout {
            val prefix = if (incoming) "call_incoming" else "call_connected"
            fun keys(zone: String) = listOf("${prefix}_${zone}_led_enabled", "${prefix}_${zone}_led_effect", "${prefix}_${zone}_led_color")
            val fanKeys = keys("fan")
            val logoKeys = keys("logo")
            fun read(keys: List<String>, colour: Int) = CallLightingState.readLed(activity,
                keys[0], keys[1], keys[2], false, if (incoming) "flashing" else "steady", colour)
            var fan = read(fanKeys, 5)
            var logo = read(logoKeys, 1)
            val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val preview = LedPreview.bind(page, { CallLightingState.isEnabled(activity) }) {
                mapOf(LiveLedPreview.Zone.FAN to fan, LiveLedPreview.Zone.LOGO to logo)
            }
            fun save(keys: List<String>, state: LedState) {
                CallLightingState.saveLed(activity, keys[0], keys[1], keys[2], state)
                preview()
                if (CallLightingState.isEnabled(activity)) HardwareServiceActions.startCallLighting(activity)
            }
            page.addView(LedZoneEditor.create(activity, "Fan LED", fan, fan = true) {
                fan = it; save(fanKeys, it)
            })
            page.addView(LedZoneEditor.create(activity, "Logo LED", logo) {
                logo = it; save(logoKeys, it)
            })
            return page
        }
        content.addView(M3Dialog.body(activity, "Lighting for each part of a call. Changes save automatically."))
        content.addView(ChoiceGroup(activity, listOf(Choice("Incoming", true, Icons.INCOMING_CALL),
            Choice("Connected", false, Icons.CALLS)), true) { incoming ->
            host.removeAllViews()
            host.addView(profile(incoming))
        }.view)
        host.addView(profile(true))
        content.addView(host)
        return content
    }
}

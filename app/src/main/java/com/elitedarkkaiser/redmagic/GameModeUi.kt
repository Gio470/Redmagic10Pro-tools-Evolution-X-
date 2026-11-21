package com.elitedarkkaiser.redmagic

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.ui.ComposeSlider
import com.elitedarkkaiser.redmagic.ui.Icons
import com.elitedarkkaiser.redmagic.ui.LedZoneEditor
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

internal object GameModeUi {
    fun editor(activity: Activity, current: GameModeProfile,
        onChanged: (GameModeProfile) -> Unit): LinearLayout {
        val m3 = M3(activity)
        var profile = current
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val preview = LedPreview.bind(content, { MasterSwitches.gameModeEnabled(activity) }) {
            mapOf(LiveLedPreview.Zone.FAN to LedState(profile.fanLedEnabled, profile.fanLedEffect, profile.fanLedColor),
                LiveLedPreview.Zone.LOGO to LedState(profile.logoLedEnabled, profile.logoLedEffect, profile.logoLedColor))
        }
        fun publish(next: GameModeProfile) {
            if (profile == next) return
            profile = next
            onChanged(profile)
            preview()
        }
        content.addView(M3Dialog.body(activity, "Used while a selected game is open. Changes save automatically."))
        val levelLabel = TextView(activity).apply {
            text = "Level ${profile.fanLevel}"
            m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
        }
        val fanSlider = ComposeSlider(activity, initialValue = profile.fanLevel.toFloat(),
            initialValueRange = 0f..5f, initialSteps = 4,
            onValueChange = { levelLabel.text = "Level ${it.roundToInt()}" },
            onValueChangeFinished = { publish(profile.copy(fanLevel = it.roundToInt())) })
        val fanControls = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(levelLabel)
            addView(fanSlider.view)
        }
        fun fanEnabled(enabled: Boolean) {
            fanControls.alpha = if (enabled) 1f else M3.Metrics.disabledAlpha
            fanSlider.isEnabled = enabled
        }
        content.addView(m3.insetGroup("Cooling").apply {
            addView(m3.row("Fan while gaming", supporting = "Use this fan speed for selected games", glyph = Icons.FAN,
                trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.fanEnabled
                    setOnCheckedChangeListener { _, checked ->
                        publish(profile.copy(fanEnabled = checked))
                        fanEnabled(checked)
                    }
                }))
            addView(fanControls)
        })
        fanEnabled(profile.fanEnabled)
        content.addView(LedZoneEditor.create(activity, "Fan LED",
            LedState(profile.fanLedEnabled, profile.fanLedEffect, profile.fanLedColor), fan = true) {
            publish(profile.copy(fanLedEnabled = it.enabled, fanLedEffect = it.effect, fanLedColor = it.color))
        })
        content.addView(LedZoneEditor.create(activity, "Logo LED",
            LedState(profile.logoLedEnabled, profile.logoLedEffect, profile.logoLedColor)) {
            publish(profile.copy(logoLedEnabled = it.enabled, logoLedEffect = it.effect, logoLedColor = it.color))
        })
        return content
    }
}

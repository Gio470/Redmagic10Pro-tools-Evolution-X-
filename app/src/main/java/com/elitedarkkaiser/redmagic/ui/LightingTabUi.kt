package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.FanLedCycle
import com.elitedarkkaiser.redmagic.state.LedState
import com.elitedarkkaiser.redmagic.storage.AppPrefs
import com.google.android.material.materialswitch.MaterialSwitch

/** Five independent Hardware cards; each feature owns its master switch and preview. */
object LightingTabUi {
    data class Result(val view: LinearLayout, val lightCards: List<IconifyGrid.Item>)

    fun create(activity: Activity, deps: LightingTabDeps): Result {
        val container = deps.scrollTabContainer()
        val m3 = M3(activity)

        // ---- The three automatic-lighting pages ----------------------------------------------
        lateinit var gameMode: SwitchSection
        val gameModeSwitch = switchFor(activity, deps.getGameModeEnabled()) { checked ->
            deps.setGameModeEnabled(checked)
            gameMode.setExpanded(checked)
        }
        val gameAppsLine = TextView(activity)
        gameMode = m3.sectionWithSwitch(
            "Game Mode", gameModeSwitch,
            glyph = Icons.GAME_MODE,
            supporting = "Hardware settings while you play"
        ).apply {
            addView(m3.groupHeader("Games"))
            addView(m3.row(
                title = "Selected games",
                supportingView = gameAppsLine.apply { text = deps.gameModeAppsSummary() },
                glyph = Icons.GAME_MODE,
                trailing = chevron(activity, m3),
                onClick = { deps.showGameModeAppPicker() }
            ))
            addView(deps.gameModeEditor())
        }

        lateinit var callLighting: SwitchSection
        val callSwitch = switchFor(activity, deps.getCallLightingEnabled()) { checked ->
            deps.setCallLightingEnabled(checked)
            callLighting.setExpanded(checked)
        }
        callLighting = m3.sectionWithSwitch(
            "Call lighting", callSwitch,
            glyph = Icons.CALLS,
            supporting = "Lights for incoming and active calls"
        ).apply {
            addView(deps.callLightingEditor())
            addView(m3.groupHeader("Fan"))
            addView(m3.row(
                title = "Pause fan during calls",
                supporting = "Turns back on when the call ends",
                glyph = Icons.PAUSE,
                trailing = switchFor(activity, deps.getPauseFanDuringCalls()) { checked ->
                    deps.setPauseFanDuringCalls(checked)
                }
            ))
        }

        lateinit var chargingMode: SwitchSection
        val chargingSwitch = switchFor(activity, deps.getChargingLedEnabled()) { checked ->
            deps.setChargingLedEnabled(checked)
            chargingMode.setExpanded(checked)
        }
        chargingMode = m3.sectionWithSwitch(
            "Charging", chargingSwitch,
            glyph = Icons.CHARGING,
            supporting = "Lights while plugged in"
        ).apply {
            addView(deps.chargingEditor())
        }

        // Not animated: this is the state the pages open in.
        gameMode.setExpanded(gameModeSwitch.isChecked, animate = false)
        callLighting.setExpanded(callSwitch.isChecked, animate = false)
        chargingMode.setExpanded(chargingSwitch.isChecked, animate = false)

        // Each automatic feature is a direct section, so SectionPages gives it its own card.
        listOf(gameMode, callLighting, chargingMode).forEach { container.addView(it) }

        val prefs = activity.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            gameAppsLine.post { gameAppsLine.text = deps.gameModeAppsSummary() }
        }
        gameAppsLine.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                gameAppsLine.text = deps.gameModeAppsSummary()
                prefs.registerOnSharedPreferenceChangeListener(listener)
            }
            override fun onViewDetachedFromWindow(v: View) {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        })

        // The two lights open their pickers directly, without another settings page in between.
        val lights = listOf(
            IconifyGrid.Item("Fan LED", "Fan colours and light patterns", Icons.FAN,
                master = IconifyGrid.Master(read = deps.getFanLedEnabled, write = deps.setFanLedEnabled),
                onClick = deps.showFanLedDialog),
            IconifyGrid.Item("Logo LED", "Rear logo colours and light patterns", Icons.LOGO,
                master = IconifyGrid.Master(read = deps.getLogoLedEnabled, write = deps.setLogoLedEnabled),
                onClick = deps.showLogoLedDialog)
        )
        m3.connectRows(container)
        m3.tintWidgets(container)
        return Result(container, lights)
    }

    /** "Breathe · Red", "Colour cycle", or "Off" -- what a light is set to, in a few words. */
    internal fun summary(state: LedState, fan: Boolean): String {
        if (!state.enabled) return "Off"
        val effect = when {
            state.effect.startsWith("preset:") -> "Preset"
            else -> EFFECTS[state.effect] ?: state.effect.replaceFirstChar { it.uppercase() }
        }
        val color = when {
            fan && state.color == FanLedCycle.CYCLE_COLOR -> "Colour cycle"
            state.effect.startsWith("preset:") -> null
            else -> COLORS[state.color]
        }
        return listOfNotNull(effect, color).joinToString(" · ")
    }

    private val EFFECTS = mapOf(
        "steady" to "Steady", "breathe" to "Breathe", "flashing" to "Flashing",
        "flow" to "Flow", "burst" to "Burst"
    )

    private val COLORS = mapOf(
        1 to "Red", 3 to "Orange", 4 to "Yellow", 5 to "Green",
        6 to "Cyan", 7 to "Blue", 8 to "Purple", 9 to "Pink"
    )

    private fun chevron(activity: Activity, m3: M3): TextView = TextView(activity).apply {
        text = Icons.CHEVRON
        gravity = Gravity.CENTER
        m3.styleGlyph(this, 14f, m3.onSurfaceVariant)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A switch already wired to its listener, for [M3.row]'s trailing slot. */
    private fun switchFor(
        activity: Activity,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): MaterialSwitch =
        MaterialSwitch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChanged(value) }
        }
}


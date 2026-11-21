package com.elitedarkkaiser.redmagic

import android.content.Context
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.CardShape
import com.elitedarkkaiser.redmagic.ui.Choice
import com.elitedarkkaiser.redmagic.ui.ChoiceGroup
import com.elitedarkkaiser.redmagic.ui.FeatureHero
import com.elitedarkkaiser.redmagic.ui.Icons
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.M3Dialog
import com.elitedarkkaiser.redmagic.ui.pageNote

/** What each shoulder trigger does. Changes save as they are made; the service reads them per press. */
internal object TriggerSetupDialog {
    val actions = listOf(
        Choice("None", "NONE", Icons.DISABLED),
        Choice("Volume up", "VOL_UP", Icons.VOLUME_UP),
        Choice("Volume down", "VOL_DOWN", Icons.VOLUME_DOWN),
        Choice("Play / pause", "MEDIA_PLAY_PAUSE", Icons.PLAY),
        Choice("Next track", "MEDIA_NEXT", Icons.NEXT),
        Choice("Previous track", "MEDIA_PREVIOUS", Icons.PREVIOUS)
    )

    fun label(value: String?): String = actions.firstOrNull { it.value == value }?.label ?: "None"

    /** "L: Volume down · R: Volume up", for the row that opens this page. */
    fun summary(context: Context): String {
        val prefs = context.getSharedPreferences("triggers", Context.MODE_PRIVATE)
        return "L: ${label(prefs.getString("left_trigger", "VOL_DOWN"))} · " +
            "R: ${label(prefs.getString("right_trigger", "VOL_UP"))}"
    }

    fun show(activity: android.app.Activity, onChanged: () -> Unit = {}) {
        val prefs = activity.getSharedPreferences("triggers", Context.MODE_PRIVATE)
        val m3 = M3(activity)
        val hero = FeatureHero(activity, Icons.TRIGGERS, CardShape.CLOVER)
        fun refreshHero() = hero.set(
            "L  ${label(prefs.getString("left_trigger", "VOL_DOWN"))}\nR  ${label(prefs.getString("right_trigger", "VOL_UP"))}",
            "Changes save as you tap.")

        fun triggerCard(title: String, glyph: String, actionKey: String, fallback: String,
            tapsKey: String, taps: List<Int>, defaultTaps: Int) = m3.block(title, glyph).apply {
            addView(ChoiceGroup(activity, actions, prefs.getString(actionKey, fallback) ?: fallback,
                ChoiceGroup.Style.CHIPS) { value ->
                prefs.edit().putString(actionKey, value).apply()
                refreshHero()
                onChanged()
            }.view)
            addView(TextView(activity).apply {
                text = "Taps to unlock"
                m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
                setPadding(m3.dp(4), m3.dp(M3.Space.lg), 0, m3.dp(M3.Space.sm))
            })
            addView(ChoiceGroup(activity, taps.map { Choice(if (it == 1) "Single" else "$it taps", it) },
                prefs.getInt(tapsKey, defaultTaps).coerceIn(taps.first(), taps.last())) {
                prefs.edit().putInt(tapsKey, it).apply()
            }.view)
        }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, "Trigger actions", glyph = Icons.TRIGGERS))
            addView(hero)
            addView(triggerCard("Left trigger", Icons.ARROW_LEFT, "left_trigger", "VOL_DOWN",
                "left_intent_unlock_tap_count", listOf(1, 2, 3, 4), 1))
            addView(triggerCard("Right trigger", Icons.ARROW_RIGHT, "right_trigger", "VOL_UP",
                "intent_unlock_tap_count", listOf(2, 3, 4), 2))
            addView(pageNote(activity, "Taps to unlock apply while Intent Unlock is on: the trigger only " +
                "acts after that many quick presses."))
        }
        refreshHero()
        M3Dialog.show(activity, M3Dialog.scroll(activity, panel))
    }
}

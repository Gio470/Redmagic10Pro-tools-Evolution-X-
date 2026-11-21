package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.HardwareController
import com.elitedarkkaiser.redmagic.state.LedState

/** Immediate animation and palette choices, shared by normal and automatic lighting pages. */
internal object LedZoneEditor {
    private data class Colour(val id: Int, val name: String, val hex: String)
    private val colours = listOf(
        Colour(1, "Red", "#FF0000"), Colour(3, "Orange", "#FF8C00"),
        Colour(4, "Yellow", "#FFD600"), Colour(5, "Green", "#00E676"),
        Colour(6, "Cyan", "#00E5FF"), Colour(7, "Blue", "#1565FF"),
        Colour(8, "Purple", "#A020F0"), Colour(9, "Pink", "#FF69B4")
    )
    private val presets = listOf(
        listOf(9, 3, 7, 5), listOf(8, 4, 6, 9), listOf(8, 6, 7, 9),
        listOf(9, 7, 7, 9), listOf(8, 6, 6, 8), listOf(6, 3, 3, 6), listOf(4, 5, 5, 4)
    )

    fun create(context: Context, title: String, initial: LedState, fan: Boolean = false,
        allowOff: Boolean = true, onChanged: (LedState) -> Unit): LinearLayout {
        val m3 = M3(context)
        var state = initial
        val panel = m3.insetGroup(title)
        val selection = TextView(context).apply { m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant) }
        panel.addView(selection)
        val swatches = mutableListOf<() -> Unit>()
        lateinit var animations: ChoiceGroup<String>

        fun refresh() {
            animations.value = when {
                allowOff && !state.enabled -> "off"
                state.effect.startsWith("preset:") -> "flow"
                else -> state.effect
            }
            selection.text = if (allowOff && !state.enabled) "Off" else {
                val effect = if (state.effect.startsWith("preset:")) "Multicolour preset"
                    else state.effect.replaceFirstChar { it.uppercase() }
                val colour = colours.firstOrNull { it.id == state.color }?.name
                    ?: if (state.color >= 10) "Multicolour" else null
                listOfNotNull(effect, colour).joinToString(" · ")
            }
            swatches.forEach { it() }
        }

        val model = LedEditorState(initial, fan, allowOff) { next ->
            state = next
            onChanged(next)
            refresh()
        }

        val effects = listOf(
            Choice("Steady", "steady", Icons.STEADY),
            Choice("Breathe", "breathe", Icons.BREATHE),
            Choice("Flash", "flashing", Icons.FLASH)
        ) + if (fan) listOf(Choice("Flow", "flow", Icons.FLOW), Choice("Burst", "burst", Icons.BURST)) else emptyList()
        panel.addView(M3Dialog.sectionLabel(context, "Animation"))
        animations = ChoiceGroup(context,
            (if (allowOff) listOf(Choice("Off", "off", Icons.DISABLED)) else emptyList()) + effects,
            when {
                allowOff && !state.enabled -> "off"
                state.effect.startsWith("preset:") -> "flow"
                else -> state.effect
            }
        ) { effect ->
            model.animation(effect)
        }
        panel.addView(animations.view)

        fun swatch(name: String, palette: List<Int>, selected: () -> Boolean, choose: () -> Unit): View {
            val circle = CycleSwatch(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            return FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, m3.dp(56), 1f)
                isFocusable = true
                isClickable = true
                addView(circle, FrameLayout.LayoutParams(m3.dp(48), m3.dp(48), Gravity.CENTER))
                setOnClickListener { if (isEnabled) choose() }
                m3.springPress(this)
                swatches += {
                    val picked = selected()
                    circle.setSwatch(palette.mapNotNull { id -> colours.firstOrNull { it.id == id }?.hex }, picked)
                    isSelected = picked
                    isEnabled = !allowOff || state.enabled
                    alpha = if (isEnabled) 1f else M3.Metrics.disabledAlpha
                    contentDescription = name + if (picked) ", selected" else ""
                }
            }
        }

        fun rows(views: List<View>) {
            views.chunked(4).forEach { chunk ->
                panel.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    chunk.forEach { addView(it) }
                    repeat(4 - chunk.size) { addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        panel.addView(M3Dialog.sectionLabel(context, "Colours"))
        rows(colours.map { colour ->
            swatch(colour.name, listOf(colour.id), { state.color == colour.id && !state.effect.startsWith("preset:") }) {
                model.colour(colour.id)
            }
        })
        if (fan) {
            rows(HardwareController.FAN_LED_PATTERNS.map { (id, palette) ->
                swatch("Colour pattern $id", palette, { state.color == id && !state.effect.startsWith("preset:") }) {
                    model.colour(id)
                }
            })
            panel.addView(M3Dialog.sectionLabel(context, "Multicolour presets"))
            rows(presets.mapIndexed { index, palette ->
                val effect = "preset:0x300210${index + 1}"
                swatch("Multicolour preset ${index + 1}", palette, { state.effect == effect }) {
                    model.preset(effect.removePrefix("preset:"))
                }
            })
        }
        refresh()
        return panel
    }
}

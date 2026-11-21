package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlin.math.roundToInt

/** Material controls saved immediately and applied to the app's real glass bars. */
object GlassPlaygroundUi {
    fun block(activity: Activity, m3: M3, onEffectsChanged: (GlassPrefs.Effects) -> Unit): LinearLayout {
        var effects = GlassPrefs.effects(activity)
        val syncs = mutableListOf<() -> Unit>()

        fun update(next: GlassPrefs.Effects) {
            effects = next
            GlassPrefs.setEffects(activity, next)
            onEffectsChanged(next)
        }

        return m3.block("Glass playground", Icons.GLASS).apply {
            addView(caption(activity, m3, "Changes apply to the bars right away."))
            addView(label(activity, m3, "Material"))
            fun slider(name: String, max: Float, read: (GlassPrefs.Effects) -> Float,
                format: (Float) -> String, write: (GlassPrefs.Effects, Float) -> GlassPrefs.Effects) {
                val value = TextView(activity).apply {
                    m3.styleText(this, M3.Type.labelLarge, m3.primary)
                    text = format(read(effects))
                }
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, m3.dp(M3.Space.sm), 0, 0)
                    addView(TextView(activity).apply {
                        text = name
                        m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(value)
                })
                val control = ComposeSlider(
                    activity,
                    initialValue = read(effects) / max,
                    initialValueRange = 0f..1f,
                    initialSteps = 0,
                    onValueChange = { fraction ->
                        update(write(effects, fraction * max))
                        value.text = format(read(effects))
                    }
                )
                syncs += {
                    control.value = read(effects) / max
                    value.text = format(read(effects))
                }
                addView(control.view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            fun dp(v: Float) = "${v.roundToInt()} dp"
            slider("Blur", GlassPrefs.MAX_BLUR_DP, { it.blurRadiusDp }, ::dp) { e, v -> e.copy(blurRadiusDp = v) }
            slider("Refraction height", GlassPrefs.MAX_REFRACTION_HEIGHT_DP, { it.refractionHeightDp }, ::dp) { e, v ->
                e.copy(refractionHeightDp = v)
            }
            slider("Refraction amount", GlassPrefs.MAX_REFRACTION_AMOUNT_DP, { it.refractionAmountDp }, ::dp) { e, v ->
                e.copy(refractionAmountDp = v)
            }
            slider("Tint", GlassPrefs.MAX_TINT, { it.tintAlpha }, { "${(it * 100).roundToInt()}%" }) { e, v ->
                e.copy(tintAlpha = v)
            }
            slider("Docked bar corner", GlassPrefs.MAX_DOCKED_CORNER_DP, { it.dockedCornerDp }, ::dp) { e, v ->
                e.copy(dockedCornerDp = v)
            }

            fun toggle(name: String, read: (GlassPrefs.Effects) -> Boolean,
                write: (GlassPrefs.Effects, Boolean) -> GlassPrefs.Effects) {
                val switch = MaterialSwitch(activity).apply {
                    isChecked = read(effects)
                    setOnCheckedChangeListener { _, on -> if (on != read(effects)) update(write(effects, on)) }
                }
                syncs += { switch.isChecked = read(effects) }
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, m3.dp(M3.Space.sm), 0, 0)
                    addView(TextView(activity).apply {
                        text = name
                        m3.styleText(this, M3.Type.bodyLarge, m3.onSurface)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(switch)
                    setOnClickListener { switch.toggle() }
                })
            }
            toggle("Dispersion", { it.dispersion }) { e, v -> e.copy(dispersion = v) }
            toggle("Depth effect", { it.depthEffect }) { e, v -> e.copy(depthEffect = v) }

            addView(LinearLayout(activity).apply {
                gravity = Gravity.END
                setPadding(0, m3.dp(M3.Space.sm), 0, 0)
                addView(M3Dialog.textButton(activity, "Reset") {
                    update(GlassPrefs.DEFAULT)
                    syncs.forEach { it() }
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun label(activity: Activity, m3: M3, text: String): TextView = TextView(activity).apply {
        this.text = text
        m3.styleText(this, M3.Type.titleSmall, m3.primary)
        setPadding(0, m3.dp(M3.Space.lg), 0, m3.dp(M3.Space.xs))
    }

    private fun caption(activity: Activity, m3: M3, text: String): TextView = TextView(activity).apply {
        this.text = text
        m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
    }

}

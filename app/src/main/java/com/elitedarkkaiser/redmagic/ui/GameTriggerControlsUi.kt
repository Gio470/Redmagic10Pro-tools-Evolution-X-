package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkProfile
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkLayout
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkStorage
import com.elitedarkkaiser.redmagic.gametrigger.NativeTgkTriggerBehavior

internal object GameTriggerControlsUi {
    fun add(activity: Activity, m3: M3, content: LinearLayout, profile: NativeTgkProfile,
        save: ((NativeTgkProfile) -> NativeTgkProfile) -> Unit) {
        /** A drawer of choices; [selected] gets a check. */
        fun choose(title: String, options: List<Triple<String, String?, () -> Unit>>, selected: Int = -1,
            glyph: String = Icons.TRIGGERS) {
            lateinit var dialog: Panel
            val rows = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                options.forEachIndexed { index, (label, description, select) ->
                    addView(m3.row(title = label, supporting = description, glyph = glyph,
                        trailing = if (index == selected) android.widget.TextView(activity).apply {
                            text = Icons.CHECK
                            m3.styleGlyph(this, 16f, m3.primary)
                        } else null,
                        onClick = { dialog.dismiss(); select() }))
                }
                m3.connectRows(this)
            }
            dialog = M3Dialog.drawer(activity, M3Dialog.panel(activity).apply {
                addView(M3Dialog.header(activity, title, glyph = glyph))
                addView(rows)
            })
        }
        content.addView(m3.groupHeader("Layouts"))
        content.addView(m3.row(title = "Layout in use", supporting = profile.activeLayout()?.name ?: "Default",
            glyph = Icons.LAYOUTS, trailing = android.widget.TextView(activity).apply {
                text = Icons.CHEVRON
                m3.styleGlyph(this, 14f, m3.onSurfaceVariant)
            }, onClick = {
                choose("Choose layout", profile.layouts.map { layout -> Triple(layout.name, null as String?, {
                    save { current -> if (current.layouts.any { it.id == layout.id }) current.copy(activeLayoutId = layout.id) else current }
                }) }, profile.layouts.indexOfFirst { it.id == profile.activeLayoutId }, Icons.LAYOUTS)
            }))
        if (profile.layouts.size < NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) {
            content.addView(m3.row(title = "Duplicate layout", supporting = "Copy its targets and trigger settings",
                glyph = Icons.COPY, onClick = {
                    val id = java.util.UUID.randomUUID().toString()
                    save { current ->
                        if (current.layouts.size >= NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE) current else {
                            val name = "Layout ${current.layouts.size + 1}"
                            val layout = current.activeLayout()?.copy(id = id, name = name) ?: NativeTgkLayout(id, name)
                            current.copy(activeLayoutId = id, layouts = current.layouts + layout)
                        }
                    }
                }))
        }
        profile.activeLayout()?.let { layout ->
            content.addView(m3.row(title = "Rename layout", supporting = layout.name, glyph = Icons.RENAME,
                onClick = {
                    lateinit var dialog: Panel
                    val name = M3Dialog.textField(activity, "Layout name").apply { setText(layout.name) }
                    dialog = M3Dialog.drawer(activity, M3Dialog.panel(activity).apply {
                        addView(M3Dialog.header(activity, "Rename layout", glyph = Icons.CONFIGURE))
                        addView(name)
                        addView(M3Dialog.buttonRow(activity,
                            M3Dialog.textButton(activity, "Cancel") { dialog.dismiss() },
                            M3Dialog.filledButton(activity, "Save") {
                                val text = name.text.toString().trim()
                                if (text.isBlank() || text.length > 40) { name.error = "Use 1–40 characters" }
                                else {
                                    save { current -> current.copy(layouts = current.layouts.map {
                                        if (it.id == layout.id) it.copy(name = text) else it
                                    }) }
                                    dialog.dismiss()
                                }
                            }))
                    })
                }))
        }
        if (profile.layouts.size > 1) content.addView(m3.row(title = "Remove layout",
            supporting = "Delete ${profile.activeLayout()?.name ?: "this layout"}", glyph = Icons.TRASH, onClick = {
                save { current ->
                    val retained = current.layouts.filterNot { it.id == profile.activeLayout()?.id }
                    if (retained.isEmpty()) current else current.copy(activeLayoutId = retained.first().id, layouts = retained)
                }
            }))

        content.addView(m3.groupHeader("Triggers"))
        fun behavior(left: Boolean) {
            val current = if (left) profile.effectiveLeftBehavior() else profile.effectiveRightBehavior()
            val count = if (left) profile.effectiveLeftRapidFireCount() else profile.effectiveRightRapidFireCount()
            val label = when (current) {
                NativeTgkTriggerBehavior.SINGLE_TOUCH -> "Single touch · one tap per press"
                NativeTgkTriggerBehavior.LONG_PRESS -> "Long press · held while you hold it"
                NativeTgkTriggerBehavior.DRAG_AIM -> "Drag aim · right-side swipes drag Shoot"
                NativeTgkTriggerBehavior.RAPID_FIRE -> "Rapid fire · up to $count taps while held"
                NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE -> "Drag aim + rapid fire · up to $count taps while held"
            }
            content.addView(m3.row(title = if (left) "Left trigger (L)" else "Right trigger (R)",
                supporting = label, glyph = when (current) {
                    NativeTgkTriggerBehavior.SINGLE_TOUCH -> Icons.TAP
                    NativeTgkTriggerBehavior.LONG_PRESS, NativeTgkTriggerBehavior.DRAG_AIM -> Icons.HOLD
                    NativeTgkTriggerBehavior.RAPID_FIRE, NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE -> Icons.RAPID
                }, onClick = {
                    choose(if (left) "Left trigger" else "Right trigger", listOf(
                        Triple("Single touch", "One tap per press",
                            { save { it.withTriggerBehavior(left, NativeTgkTriggerBehavior.SINGLE_TOUCH, 0) } }),
                        Triple("Long press", "Held while you hold the trigger",
                            { save { it.withTriggerBehavior(left, NativeTgkTriggerBehavior.LONG_PRESS, 0) } }),
                        Triple("Rapid fire", "Repeat taps while held; release to stop",
                            { rapidFire(activity, left, count, save) }),
                        Triple("Drag aim", "Hold Shoot and swipe on the right to aim",
                            { save { it.withTriggerBehavior(left, NativeTgkTriggerBehavior.DRAG_AIM, 0) } }),
                        Triple("Drag aim + rapid fire", "Aim with right-side swipes; release to stop firing",
                            { rapidFire(activity, left, count, save, NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE) })
                    ), current.ordinal)
                }))
        }
        behavior(true)
        behavior(false)
    }

    private fun rapidFire(activity: Activity, left: Boolean, currentCount: Int,
        save: ((NativeTgkProfile) -> NativeTgkProfile) -> Unit,
        behavior: NativeTgkTriggerBehavior = NativeTgkTriggerBehavior.RAPID_FIRE) {
        var selected = currentCount.takeIf { it in NativeTgkStorage.rapidFireRange }
            ?: NativeTgkStorage.DEFAULT_RAPID_FIRE_COUNT
        lateinit var dialog: Panel
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(RapidFireCountControl(activity, selected) { selected = it }.view)
        }
        dialog = M3Dialog.drawer(activity, M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, if (left) "Left trigger rapid fire" else "Right trigger rapid fire",
                "Maximum taps per hold. Release the trigger to stop.", Icons.TRIGGERS))
            addView(body)
            addView(M3Dialog.actionRow(activity, M3Dialog.tonalButton(activity, "Cancel") { dialog.dismiss() },
                M3Dialog.filledButton(activity, "Save") {
                    save { it.withTriggerBehavior(left, behavior, selected) }
                    dialog.dismiss()
                }))
        })
    }
}


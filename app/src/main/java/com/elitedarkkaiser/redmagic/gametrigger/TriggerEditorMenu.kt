package com.elitedarkkaiser.redmagic.gametrigger

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elitedarkkaiser.redmagic.ui.Icons
import com.elitedarkkaiser.redmagic.ui.M3
import com.elitedarkkaiser.redmagic.ui.RapidFireCountControl
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * The floating trigger editor: a solid Material You panel over the game, with every control
 * inside this one touchable window.
 */
class TriggerEditorMenu(
    private val context: Context,
    private val draft: TriggerEditorDraft,
    private val display: TriggerDisplayState,
    val state: State,
    private val updated: () -> Unit,
    private val orientationChanged: (NativeTgkOrientation) -> Unit,
    private val save: (Boolean) -> Unit,
    private val cancel: () -> Unit
) {
    class State {
        var leftSelected = true
        var collapsed = false
        var locked = false
        var step = 1
        var tab = TAB_PLACE
    }

    private val m3 = M3(context)
    val view = column().apply {
        background = m3.surfaceShape(m3.surfaceContainerHigh, 28, m3.outlineVariant)
        clipToOutline = true
        elevation = m3.dp(8).toFloat()
        setPadding(m3.dp(12), m3.dp(6), m3.dp(12), m3.dp(12))
    }

    /** What the service listens to for dragging the panel: the whole header. */
    lateinit var dragHandle: View
        private set
    private lateinit var subtitle: TextView
    private lateinit var badge: TextView
    private lateinit var status: TextView
    private var positionLabel: TextView? = null
    private var undoTool: View? = null
    private var errorMessage: String? = null
    private val ready get() = display.orientation == draft.orientation

    init { render() }

    // ---- Small building blocks -------------------------------------------------------------

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun text(label: String, style: M3.TypeStyle = M3.Type.bodySmall, color: Int = m3.onSurfaceVariant) =
        TextView(context).apply {
            text = label
            m3.styleText(this, style, color)
        }

    private fun glyph(icon: String, sizeSp: Float, color: Int) = TextView(context).apply {
        text = icon
        gravity = Gravity.CENTER
        m3.styleGlyph(this, sizeSp, color)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A leading icon on its expressive shape; primary when its row is the selected one. */
    private fun shaped(icon: String, on: Boolean) = com.elitedarkkaiser.redmagic.ui.ShapeChip(context, icon,
        if (on) m3.primary else m3.secondaryContainer, if (on) m3.onPrimary else m3.onSecondaryContainer, 15f)

    private fun lp(width: Int, height: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(width, height, weight)

    /** A round icon-only button, 40dp, with a 48dp touch target. */
    private fun iconButton(icon: String, description: String, enabled: Boolean = true,
        filled: Boolean = false, click: () -> Unit): View = FrameLayout(context).apply {
        minimumWidth = m3.dp(48)
        minimumHeight = m3.dp(48)
        val face = glyph(icon, 15f, if (filled) m3.onPrimaryContainer else m3.onSurface).apply {
            background = if (filled) m3.filled(m3.primaryContainer, 20, m3.onPrimaryContainer)
            else m3.filled(m3.surfaceContainerHighest, 20, m3.onSurface)
        }
        addView(face, FrameLayout.LayoutParams(m3.dp(40), m3.dp(40), Gravity.CENTER))
        contentDescription = description
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.38f
        face.isClickable = false
        setOnClickListener { if (isEnabled) click() }
        m3.springPress(this)
    }

    /** A tool tile: icon over a short label, for the Place tab's row of actions. */
    private fun tool(icon: String, label: String, enabled: Boolean = true, active: Boolean = false,
        click: () -> Unit): View = column().apply {
        gravity = Gravity.CENTER
        setPadding(0, m3.dp(10), 0, m3.dp(8))
        background = if (active) m3.filled(m3.secondaryContainer, 16, m3.onSecondaryContainer)
        else m3.filled(m3.surfaceContainerHighest, 16, m3.onSurface)
        val color = if (active) m3.onSecondaryContainer else m3.onSurface
        addView(shaped(icon, active), lp(m3.dp(36), m3.dp(36)))
        addView(text(label, M3.Type.labelMedium, color).apply {
            gravity = Gravity.CENTER
            setPadding(0, m3.dp(4), 0, 0)
        })
        contentDescription = label
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.38f
        setOnClickListener { if (isEnabled) click() }
        if (Build.VERSION.SDK_INT >= 30) stateDescription = if (active) "On" else null
        m3.springPress(this)
    }

    /**
     * A connected segmented control: equal segments on a shared track, the selected one filled.
     * [colors] lets a segment use its own fill when selected (the L/R targets' own colours).
     */
    private fun segmented(labels: List<String>, selected: Int, colors: List<Int?> = labels.map { null },
        onSelect: (Int) -> Unit): View = LinearLayout(context).apply {
        background = m3.surfaceShape(m3.surfaceContainerHighest, 24)
        setPadding(m3.dp(4), m3.dp(4), m3.dp(4), m3.dp(4))
        labels.forEachIndexed { index, label ->
            val on = index == selected
            val fill = colors[index] ?: m3.primary
            val ink = if (colors[index] != null) Color.WHITE else m3.onPrimary
            addView(text(label, M3.Type.labelLarge, if (on) ink else m3.onSurfaceVariant).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = if (on) m3.filled(fill, 20, ink) else m3.filled(Color.TRANSPARENT, 20, m3.onSurface)
                contentDescription = label
                if (Build.VERSION.SDK_INT >= 30) stateDescription = if (on) "Selected" else null
                setOnClickListener { if (!on) onSelect(index) }
            }, lp(0, m3.dp(40), 1f))
        }
    }

    /** A titled group on its own tonal surface. */
    private fun section(parent: LinearLayout, label: String?, content: (LinearLayout) -> Unit) {
        val group = column().apply {
            background = m3.surfaceShape(m3.surfaceContainerLow, 20)
            setPadding(m3.dp(12), m3.dp(10), m3.dp(12), m3.dp(12))
            if (label != null) addView(text(label, M3.Type.labelLarge, m3.primary).apply {
                setPadding(m3.dp(4), 0, 0, m3.dp(8))
            })
        }
        content(group)
        parent.addView(group, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = m3.dp(8)
        })
    }

    private fun gap(parent: LinearLayout, dp: Int) =
        parent.addView(View(context), lp(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(dp)))

    private fun edit(action: () -> Unit) {
        errorMessage = null
        action()
        updated()
        render()
    }

    // ---- Called by the service ---------------------------------------------------------------

    fun select(left: Boolean) {
        state.leftSelected = left
        updated()
        if (!state.collapsed && state.tab in listOf(TAB_PLACE, TAB_TRIGGERS)) render()
    }

    fun showError(message: String) {
        errorMessage = message
        refreshPosition()
    }

    fun refreshPosition() {
        undoTool?.apply {
            isEnabled = draft.canUndo && !state.locked
            alpha = if (isEnabled) 1f else 0.38f
        }
        val rect = if (state.leftSelected) draft.mapping?.left else draft.mapping?.right
        positionLabel?.text = rect?.let { "X ${(it.left + it.right) / 2}   ·   Y ${(it.top + it.bottom) / 2}" }
            ?: "Place both targets to begin"
        if (::subtitle.isInitialized) {
            subtitle.text = "${draft.layout.name} · ${orientationLabel(draft.orientation)}"
            badge.text = if (draft.hasChanges) "Unsaved" else "Saved"
            badge.setTextColor(if (draft.hasChanges) m3.onTertiaryContainer else m3.onSurfaceVariant)
            badge.background = m3.surfaceShape(
                if (draft.hasChanges) m3.tertiaryContainer else m3.surfaceContainerHighest, 12)
            val message = errorMessage ?: if (!ready) {
                "Turn the phone to ${draft.orientation.name.lowercase()} to place these targets."
            } else null
            status.visibility = if (message != null) View.VISIBLE else View.GONE
            status.text = message
            status.setTextColor(if (errorMessage != null) m3.error else m3.onSurfaceVariant)
        }
    }

    // ---- Layout ------------------------------------------------------------------------------

    fun render() {
        view.removeAllViews()
        positionLabel = null
        undoTool = null
        header()

        if (!ready) {
            gap(view, 8)
            view.addView(m3.button("Edit ${draft.orientation.opposite().name.lowercase()} instead",
                M3.ButtonKind.Tonal, onClick = { orientationChanged(display.orientation) }),
                lp(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(48)))
        } else if (!state.collapsed) {
            gap(view, 8)
            tabs()
            val body = column()
            when (state.tab) {
                TAB_PLACE -> place(body)
                TAB_TRIGGERS -> triggers(body)
                TAB_LAYOUTS -> layouts(body)
                else -> options(body)
            }
            view.addView(ScrollView(context).apply {
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                addView(body)
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT,
                (display.height - m3.dp(260)).coerceIn(m3.dp(96), m3.dp(320))).apply {
                    topMargin = m3.dp(10)
                })
        }

        gap(view, 8)
        view.addView(LinearLayout(context).apply {
            addView(m3.button("Save", M3.ButtonKind.Tonal, onClick = { save(false) }).apply {
                isEnabled = ready
                alpha = if (ready) 1f else 0.38f
            }, lp(0, m3.dp(48), 1f))
            addView(m3.button("Save & enable", M3.ButtonKind.Filled, onClick = { save(true) }).apply {
                isEnabled = ready
                alpha = if (ready) 1f else 0.38f
            }, lp(0, m3.dp(48), 1.4f).apply { marginStart = m3.dp(8) })
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        refreshPosition()
        updated()
    }

    private fun header() {
        val handle = column()
        // The grip says "this moves" before anyone tries.
        handle.addView(FrameLayout(context).apply {
            addView(View(context).apply { background = m3.surfaceShape(m3.outlineVariant, 2) },
                FrameLayout.LayoutParams(m3.dp(32), m3.dp(4), Gravity.CENTER))
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(14)))
        handle.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                setImageDrawable(runCatching { context.packageManager.getApplicationIcon(draft.profile.packageName) }
                    .getOrNull())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, lp(m3.dp(32), m3.dp(32)).apply { marginEnd = m3.dp(10) })
            addView(column().apply {
                addView(text(draft.profile.appLabel, M3.Type.titleMedium, m3.onSurface).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                subtitle = text("").apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(subtitle)
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            badge = text("", M3.Type.labelSmall).apply {
                setPadding(m3.dp(8), m3.dp(4), m3.dp(8), m3.dp(4))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            addView(badge, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = m3.dp(2)
            })
            addView(iconButton(if (state.collapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP,
                if (state.collapsed) "Expand editor" else "Collapse editor") {
                state.collapsed = !state.collapsed
                render()
            })
            addView(iconButton(Icons.XMARK, "Close editor without saving", click = cancel))
        })
        handle.contentDescription = "Drag to move the trigger editor"
        dragHandle = handle
        view.addView(handle)
        status = text("").apply {
            setPadding(m3.dp(4), m3.dp(6), m3.dp(4), 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        view.addView(status)
    }

    private fun tabs() {
        val items = listOf(
            Triple(TAB_PLACE, "Place", Icons.TARGET),
            Triple(TAB_TRIGGERS, "Triggers", Icons.TAP),
            Triple(TAB_LAYOUTS, "Layouts", Icons.LAYOUTS),
            Triple(TAB_OPTIONS, "Options", Icons.CONFIGURE)
        )
        view.addView(LinearLayout(context).apply {
            background = m3.surfaceShape(m3.surfaceContainerHighest, 24)
            setPadding(m3.dp(4), m3.dp(4), m3.dp(4), m3.dp(4))
            items.forEach { (key, label, icon) ->
                val on = state.tab == key
                val ink = if (on) m3.onSecondaryContainer else m3.onSurfaceVariant
                addView(column().apply {
                    gravity = Gravity.CENTER
                    setPadding(0, m3.dp(6), 0, m3.dp(6))
                    background = if (on) m3.filled(m3.secondaryContainer, 20, ink)
                    else m3.filled(Color.TRANSPARENT, 20, m3.onSurface)
                    addView(glyph(icon, 14f, ink), lp(m3.dp(20), m3.dp(20)))
                    addView(text(label, M3.Type.labelMedium, ink).apply { gravity = Gravity.CENTER })
                    contentDescription = label
                    if (Build.VERSION.SDK_INT >= 30) stateDescription = if (on) "Selected" else null
                    setOnClickListener { if (!on) { state.tab = key; render() } }
                }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** L or R, in the targets' own colours so it matches what is on screen. */
    private fun sideSelector(parent: LinearLayout) {
        parent.addView(segmented(listOf("L  ·  Left", "R  ·  Right"), if (state.leftSelected) 0 else 1,
            listOf(LEFT_COLOR, RIGHT_COLOR)) { select(it == 0) },
            lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // ---- Tabs --------------------------------------------------------------------------------

    private fun place(parent: LinearLayout) {
        section(parent, null) { group ->
            sideSelector(group)
            positionLabel = text("", M3.Type.labelLarge, m3.onSurface).apply {
                gravity = Gravity.CENTER
                setPadding(0, m3.dp(10), 0, m3.dp(4))
            }.also { group.addView(it, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }

            // The D-pad, with the step size in the middle: tap it to cycle 1, 5 and 10 px.
            val pad = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
            fun padRow(vararg cells: View?) = pad.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                cells.forEach { cell ->
                    addView(cell ?: View(context), lp(m3.dp(56), m3.dp(52)))
                }
            })
            val unlocked = !state.locked
            padRow(null, iconButton(Icons.ARROW_UP, "Move up", unlocked) {
                edit { draft.nudge(state.leftSelected, 0, -state.step) } }, null)
            padRow(iconButton(Icons.ARROW_LEFT, "Move left", unlocked) {
                edit { draft.nudge(state.leftSelected, -state.step, 0) } },
                text("${state.step} px", M3.Type.labelLarge, m3.onPrimaryContainer).apply {
                    gravity = Gravity.CENTER
                    background = m3.filled(m3.primaryContainer, 22, m3.onPrimaryContainer)
                    contentDescription = "Step size ${state.step} pixels. Tap to change."
                    setOnClickListener {
                        state.step = when (state.step) { 1 -> 5; 5 -> 10; else -> 1 }
                        render()
                    }
                },
                iconButton(Icons.ARROW_RIGHT, "Move right", unlocked) {
                    edit { draft.nudge(state.leftSelected, state.step, 0) } })
            padRow(null, iconButton(Icons.ARROW_DOWN, "Move down", unlocked) {
                edit { draft.nudge(state.leftSelected, 0, state.step) } }, null)
            group.addView(pad, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        section(parent, "Tools") { group ->
            group.addView(LinearLayout(context).apply {
                val unlocked = !state.locked
                addView(tool(Icons.SWAP, "Swap", unlocked) { edit(draft::swap) }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(tool(Icons.ALIGN, "Align", unlocked) { edit(draft::align) },
                    lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = m3.dp(6) })
                val undo = tool(Icons.UNDO, "Undo", draft.canUndo && unlocked) { edit { draft.undo() } }
                undoTool = undo
                addView(undo, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = m3.dp(6) })
                addView(tool(if (state.locked) Icons.LOCK else Icons.LOCK_OPEN,
                    if (state.locked) "Locked" else "Lock", active = state.locked) {
                    state.locked = !state.locked
                    render()
                }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = m3.dp(6) })
            })
            group.addView(m3.button("Reset positions", M3.ButtonKind.Text, onClick = {
                edit { draft.reset(display, m3.dp(18)) }
            }).apply {
                isEnabled = !state.locked
                alpha = if (state.locked) 0.38f else 1f
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(48)).apply { topMargin = m3.dp(4) })
        }
        parent.addView(text("Drag L or R on the screen. Tap one to select it, then fine-tune here.").apply {
            setPadding(m3.dp(4), 0, m3.dp(4), 0)
        })
    }

    private fun triggers(parent: LinearLayout) {
        val left = state.leftSelected
        val current = if (left) draft.profile.effectiveLeftBehavior() else draft.profile.effectiveRightBehavior()
        val count = if (left) draft.profile.effectiveLeftRapidFireCount() else draft.profile.effectiveRightRapidFireCount()
        section(parent, null) { group -> sideSelector(group) }
        section(parent, "When pressed") { group ->
            fun option(behavior: NativeTgkTriggerBehavior, icon: String, title: String, description: String,
                apply: () -> Unit) {
                val on = current == behavior
                group.addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = m3.dp(64)
                    setPadding(m3.dp(12), m3.dp(8), m3.dp(12), m3.dp(8))
                    background = if (on) m3.filled(m3.secondaryContainer, 16, m3.onSecondaryContainer)
                    else m3.filled(m3.surfaceContainerHighest, 16, m3.onSurface)
                    val ink = if (on) m3.onSecondaryContainer else m3.onSurface
                    addView(shaped(icon, on), lp(m3.dp(36), m3.dp(36)).apply { marginEnd = m3.dp(12) })
                    addView(column().apply {
                        addView(text(title, M3.Type.titleSmall, ink))
                        addView(text(description, M3.Type.bodySmall,
                            if (on) m3.onSecondaryContainer else m3.onSurfaceVariant))
                    }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (on) addView(glyph(Icons.CHECK, 14f, ink), lp(m3.dp(24), m3.dp(24)))
                    contentDescription = "$title. $description"
                    if (Build.VERSION.SDK_INT >= 30) stateDescription = if (on) "Selected" else null
                    setOnClickListener { if (!on) edit(apply) }
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = m3.dp(6)
                })
            }
            option(NativeTgkTriggerBehavior.SINGLE_TOUCH, Icons.TAP, "Single touch", "One tap per press") {
                draft.setBehavior(left, NativeTgkTriggerBehavior.SINGLE_TOUCH)
            }
            option(NativeTgkTriggerBehavior.LONG_PRESS, Icons.HOLD, "Long press", "Held while you hold the trigger") {
                draft.setBehavior(left, NativeTgkTriggerBehavior.LONG_PRESS)
            }
            option(NativeTgkTriggerBehavior.RAPID_FIRE, Icons.RAPID, "Rapid fire", "Repeat taps while held; release to stop") {
                draft.setBehavior(left, NativeTgkTriggerBehavior.RAPID_FIRE,
                    count.takeIf { it in NativeTgkStorage.rapidFireRange } ?: NativeTgkStorage.DEFAULT_RAPID_FIRE_COUNT)
            }
            option(NativeTgkTriggerBehavior.DRAG_AIM, Icons.HOLD, "Drag aim",
                "Hold Shoot and swipe on the right to aim") {
                draft.setBehavior(left, NativeTgkTriggerBehavior.DRAG_AIM)
            }
            option(NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE, Icons.RAPID, "Drag aim + rapid fire",
                "Aim with right-side swipes; release to stop firing") {
                draft.setBehavior(left, NativeTgkTriggerBehavior.DRAG_AIM_RAPID_FIRE,
                    count.takeIf { it in NativeTgkStorage.rapidFireRange } ?: NativeTgkStorage.DEFAULT_RAPID_FIRE_COUNT)
            }
            if (current.isRapidFire) {
                group.addView(RapidFireCountControl(context, count) { value ->
                    edit { draft.setBehavior(left, current, value) }
                }.view)
            }
        }
        parent.addView(text("Each trigger has its own setting.").apply { setPadding(m3.dp(4), 0, m3.dp(4), 0) })
    }

    private fun layouts(parent: LinearLayout) {
        section(parent, "This game's layouts") { group ->
            draft.profile.layouts.forEach { layout ->
                val on = layout.id == draft.profile.activeLayoutId
                group.addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = m3.dp(52)
                    setPadding(m3.dp(12), 0, m3.dp(12), 0)
                    background = if (on) m3.filled(m3.secondaryContainer, 16, m3.onSecondaryContainer)
                    else m3.filled(m3.surfaceContainerHighest, 16, m3.onSurface)
                    val ink = if (on) m3.onSecondaryContainer else m3.onSurface
                    addView(shaped(Icons.LAYOUTS, on), lp(m3.dp(36), m3.dp(36)).apply { marginEnd = m3.dp(12) })
                    addView(text(layout.name, M3.Type.titleSmall, ink), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (on) addView(glyph(Icons.CHECK, 14f, ink), lp(m3.dp(24), m3.dp(24)))
                    contentDescription = layout.name
                    if (Build.VERSION.SDK_INT >= 30) stateDescription = if (on) "In use" else null
                    setOnClickListener {
                        if (!on) edit { draft.selectLayout(layout.id); draft.attachDisplay(this@TriggerEditorMenu.display, m3.dp(18)) }
                    }
                }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = m3.dp(6)
                })
            }
            val canAdd = draft.profile.layouts.size < NativeTgkStorage.MAX_LAYOUTS_PER_PROFILE
            group.addView(m3.button("Duplicate this layout", M3.ButtonKind.Tonal, onClick = {
                edit { draft.duplicateLayout() }
            }).apply {
                isEnabled = canAdd
                alpha = if (canAdd) 1f else 0.38f
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(48)).apply { topMargin = m3.dp(4) })
        }
        parent.addView(text("Up to five per game. A copy includes both orientations and trigger settings.").apply {
            setPadding(m3.dp(4), 0, m3.dp(4), 0)
        })
    }

    private fun toggle(parent: LinearLayout, label: String, description: String, checked: Boolean,
        changed: (Boolean) -> Unit) {
        val line = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = m3.dp(60)
            setPadding(m3.dp(4), 0, 0, 0)
        }
        line.addView(column().apply {
            addView(text(label, M3.Type.titleSmall, m3.onSurface))
            addView(text(description))
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = m3.dp(8) })
        val switch = MaterialSwitch(context).apply {
            isChecked = checked
            contentDescription = label
            m3.tintSwitch(this)
            setOnCheckedChangeListener { _, value -> edit { changed(value) } }
        }
        line.addView(switch)
        line.setOnClickListener { switch.isChecked = !switch.isChecked }
        parent.addView(line)
    }

    private fun options(parent: LinearLayout) {
        section(parent, "While playing") { group ->
            toggle(group, "Haptics", "Vibrate on each trigger press", draft.profile.hapticsEnabled, draft::setHaptics)
        }
        section(parent, "Editing") { group ->
            val values = NativeTgkOrientation.entries
            group.addView(segmented(values.map { orientationLabel(it) }, values.indexOf(draft.orientation)) {
                orientationChanged(values[it])
            }, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        parent.addView(text("Drag the header to move this panel. Close discards unsaved changes.").apply {
            setPadding(m3.dp(4), 0, m3.dp(4), 0)
        })
    }

    private fun orientationLabel(value: NativeTgkOrientation) =
        value.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun NativeTgkOrientation.opposite() =
        if (this == NativeTgkOrientation.LANDSCAPE) NativeTgkOrientation.PORTRAIT else NativeTgkOrientation.LANDSCAPE

    companion object {
        const val TAB_PLACE = "Targets"
        const val TAB_TRIGGERS = "Behavior"
        const val TAB_LAYOUTS = "Layouts"
        const val TAB_OPTIONS = "Options"
        /** The same colours the L and R targets are drawn in. */
        val LEFT_COLOR = Color.rgb(232, 88, 101)
        val RIGHT_COLOR = Color.rgb(77, 153, 246)
    }
}


package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.gametrigger.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** The game library and each game's trigger controls. */
object GameTriggerUi {
    /** The same hardware card as Display density, opening the library directly. */
    fun card(activity: Activity) = IconifyGrid.Item(
        title = "Game trigger mapping",
        subtitle = "L/R targets, layouts and controls for each game",
        glyph = Icons.TRIGGERS,
        master = IconifyGrid.Master(
            read = { NativeTgkStorage.enabled(activity) },
            write = { NativeTgkStorage.setEnabled(activity, it) }
        ),
        onClick = { show(activity) }
    )

    fun show(activity: Activity) {
        TriggerRuntimeService.sync(activity)
        val m3 = M3(activity)
        val content = column(activity)
        lateinit var rebuild: () -> Unit
        rebuild = {
            content.removeAllViews()
            val profiles = NativeTgkStorage.readProfiles(activity)
            val master = NativeTgkStorage.enabled(activity)

            content.addView(statusCard(activity, m3, master, profiles) { on ->
                NativeTgkStorage.setEnabled(activity, on); rebuild()
            })

            content.addView(m3.groupHeader("Games"))
            if (profiles.isEmpty()) {
                content.addView(IconifyKit.infoBlock(activity,
                    "Add a game, then drag L and R onto its on-screen buttons.").first)
            }
            profiles.forEach { profile ->
                content.addView(gameRow(activity, m3, profile, master) {
                    showGame(activity, profile.packageName, rebuild)
                })
            }
            content.addView(m3.row("Add a game", "Pick from your installed games and apps",
                glyph = Icons.PLUS, onClick = {
                    pickGame(activity) { pkg, label ->
                        val existing = NativeTgkStorage.getProfile(activity, pkg)
                        if (existing != null || NativeTgkStorage.saveProfile(activity, NativeTgkProfile(pkg, label))) {
                            rebuild()
                            showGame(activity, pkg, rebuild)
                        } else toast(activity, "Couldn't add this game")
                    }
                }))
            m3.connectRows(content)
            m3.tintWidgets(content)
        }
        rebuild()
        val handle = showPage(activity, "Game trigger mapping",
            "Turn shoulder-trigger presses into taps in your games.", content)
        watch(activity, handle, rebuild)
        TriggerSetup.ensure(activity) { if (!activity.isDestroyed && handle.isShowing) rebuild() }
        rebuild()
    }

    /** What mapping is doing right now, and the switch for all of it. */
    private fun statusCard(activity: Activity, m3: M3, master: Boolean, profiles: List<NativeTgkProfile>,
        onToggle: (Boolean) -> Unit): View {
        val ready = profiles.count { it.enabled && it.hasAnyCompleteMapping() }
        val (fill, ink) = m3.homeCardColors(if (master) M3.HomeTint.Primary else M3.HomeTint.Neutral)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = m3.cardShape(fill, M3.Shape.extraLarge)
            setPadding(m3.dp(20), m3.dp(18), m3.dp(16), m3.dp(18))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = m3.dp(M3.Space.sm) }
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ShapeIcon(activity, Icons.TRIGGERS, CardShape.forGlyph(Icons.TRIGGERS), false)
                    .apply { setOn(master, animate = false) },
                    LinearLayout.LayoutParams(m3.dp(48), m3.dp(48)).apply { marginEnd = m3.dp(14) })
                addView(column(activity).apply {
                    addView(text(activity, if (master) "Mapping is on" else "Mapping is off",
                        M3.Type.titleLarge, ink))
                    addView(text(activity, when {
                        !master -> "Saved targets stay ready for when you turn it back on."
                        ready == 0 -> "Add a game and place its targets to start."
                        ready == 1 -> "1 game ready"
                        else -> "$ready games ready"
                    }, M3.Type.bodyMedium, m3.onSurfaceVariant))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(MaterialSwitch(activity).apply {
                    isChecked = master
                    contentDescription = "Game trigger mapping"
                    setOnCheckedChangeListener { _, on -> onToggle(on) }
                })
            })
            if (master) {
                addView(LinearLayout(activity).apply {
                    setPadding(0, m3.dp(14), 0, 0)
                    addView(fact(activity, m3, "Now", GameTrigger.lastMessage),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
                    addView(fact(activity, m3, "Engine", GameTrigger.engine()),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = m3.dp(8)
                        })
                })
            }
        }
    }

    private fun fact(activity: Activity, m3: M3, label: String, value: String): View =
        column(activity).apply {
            background = m3.surfaceShape(m3.surfaceContainerHighest, 16)
            setPadding(m3.dp(12), m3.dp(10), m3.dp(12), m3.dp(10))
            addView(text(activity, label, M3.Type.labelMedium, m3.onSurfaceVariant))
            addView(text(activity, value, M3.Type.bodyMedium, m3.onSurface).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

    /** One game: its icon, name, layout and which orientations have targets, and its status. */
    private fun gameRow(activity: Activity, m3: M3, profile: NativeTgkProfile, master: Boolean,
        open: () -> Unit): View {
        fun mark(orientation: NativeTgkOrientation) =
            "${orientation.label()} ${if (profile.hasCompleteMapping(orientation)) "✓" else "–"}"
        val status = when {
            !profile.enabled -> "Paused"
            !profile.hasAnyCompleteMapping() -> "Needs targets"
            !master -> "Mapping off"
            else -> "Ready"
        }
        val row = m3.row(profile.appLabel,
            "${profile.activeLayout()?.name ?: "Default"} · ${mark(NativeTgkOrientation.LANDSCAPE)} · " +
                mark(NativeTgkOrientation.PORTRAIT),
            trailing = chip(activity, status, status == "Ready"),
            onClick = open)
        row.addView(ImageView(activity).apply {
            setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(profile.packageName) }
                .getOrElse { activity.getDrawable(android.R.drawable.sym_def_app_icon) })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 0, LinearLayout.LayoutParams(m3.dp(40), m3.dp(40)).apply { marginEnd = m3.dp(16) })
        return row
    }

    private fun showGame(activity: Activity, packageName: String, onChanged: () -> Unit) {
        val initial = NativeTgkStorage.getProfile(activity, packageName) ?: return
        val m3 = M3(activity)
        val content = column(activity)
        lateinit var rebuild: () -> Unit
        lateinit var handle: Panel
        fun save(change: (NativeTgkProfile) -> NativeTgkProfile) {
            if (!NativeTgkStorage.updateProfile(activity, packageName, change)) toast(activity, "Couldn't save this change")
            GameTrigger.refresh(activity)
            rebuild()
            onChanged()
        }
        rebuild = rebuild@{
            val profile = NativeTgkStorage.getProfile(activity, packageName) ?: return@rebuild
            content.removeAllViews()
            val enableRow = m3.row("Use for this game",
                if (profile.hasAnyCompleteMapping()) "Apply ${profile.activeLayout()?.name ?: "the layout"} when it opens"
                else "Place targets first",
                trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.enabled
                    setOnCheckedChangeListener { _, on -> save { it.copy(enabled = on) } }
                })
            enableRow.addView(ImageView(activity).apply {
                setImageDrawable(runCatching { activity.packageManager.getApplicationIcon(packageName) }.getOrNull())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, 0, LinearLayout.LayoutParams(m3.dp(40), m3.dp(40)).apply { marginEnd = m3.dp(16) })
            content.addView(enableRow)
            if (profile.enabled && !NativeTgkStorage.enabled(activity)) {
                content.addView(IconifyKit.infoBlock(activity,
                    "Mapping is off for all games. Tap Save & enable in the editor to turn it on.").first)
            }
            content.addView(m3.groupHeader("Targets"))
            listOf(NativeTgkOrientation.LANDSCAPE, NativeTgkOrientation.PORTRAIT).forEach { orientation ->
                val set = profile.hasCompleteMapping(orientation)
                content.addView(m3.row(orientation.label(),
                    if (set) "Placed · tap to edit" else "Not placed yet",
                    glyph = Icons.TARGET,
                    trailing = chip(activity, if (set) "Edit" else "Place", !set),
                    onClick = { editTargets(activity, profile, orientation) }))
            }
            GameTriggerControlsUi.add(activity, m3, content, profile, ::save)
            content.addView(m3.groupHeader("While playing"))
            content.addView(m3.row("Haptics", "Vibrate on each trigger press",
                glyph = Icons.HAPTICS, trailing = MaterialSwitch(activity).apply {
                    isChecked = profile.hapticsEnabled
                    setOnCheckedChangeListener { _, on -> save { it.copy(hapticsEnabled = on) } }
                }))
            content.addView(m3.groupHeader("Manage"))
            content.addView(m3.row("Remove game", "Delete its layouts and targets",
                glyph = Icons.TRASH, onClick = {
                    MaterialAlertDialogBuilder(activity).setTitle("Remove ${profile.appLabel}?")
                        .setMessage("Its layouts and L/R targets will be deleted.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                            NativeTgkStorage.removeProfile(activity, packageName)
                            GameTrigger.refresh(activity)
                            handle.dismiss()
                        }.show()
                }))
            m3.connectRows(content)
            m3.tintWidgets(content)
        }
        rebuild()
        handle = showPage(activity, initial.appLabel, "Targets, triggers and layouts for this game.", content)
        watch(activity, handle, rebuild, onChanged)
    }

    private fun editTargets(activity: Activity, profile: NativeTgkProfile, orientation: NativeTgkOrientation) {
        fun open() {
            NativeTgkStorage.getProfile(activity, profile.packageName)?.let {
                TriggerEditorService.open(activity, it, orientation)
            }
        }
        if (TriggerSetup.state(activity).ready) { open(); return }
        toast(activity, "Preparing trigger permissions…")
        TriggerSetup.ensure(activity) { result ->
            if (!activity.isDestroyed) {
                if (result.state.overlay) open()
                else {
                    toast(activity, result.message)
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")))
                }
            }
        }
    }

    private fun chip(context: Context, label: String, selected: Boolean): TextView {
        val m3 = M3(context)
        return text(context, label, M3.Type.labelMedium, if (selected) m3.onPrimaryContainer else m3.onSurfaceVariant).apply {
            gravity = Gravity.CENTER
            setPadding(m3.dp(8), m3.dp(6), m3.dp(8), m3.dp(6))
            background = m3.surfaceShape(if (selected) m3.primaryContainer else m3.surfaceContainerHighest, 12)
        }
    }
    private fun column(context: Context) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private fun text(context: Context, value: String, style: M3.TypeStyle, color: Int) = TextView(context).apply {
        text = value; M3(context).styleText(this, style, color)
    }
    private fun NativeTgkOrientation.label() = name.lowercase().replaceFirstChar { it.uppercase() }
    private fun toast(context: Context, value: String) = Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
    private fun showPage(activity: Activity, title: String, supporting: String, content: View): Panel =
        M3Dialog.show(activity, M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, title, supporting, Icons.TRIGGERS))
            addView(M3Dialog.scroll(activity, content).apply { M3Dialog.scrollsUnderBar(this) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    private fun watch(activity: Activity, handle: Panel, rebuild: () -> Unit, dismissed: () -> Unit = {}) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(current: Activity) { if (current === activity && handle.isShowing) rebuild() }
            override fun onActivityDestroyed(current: Activity) { if (current === activity) activity.application.unregisterActivityLifecycleCallbacks(this) }
            override fun onActivityCreated(current: Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(current: Activity) = Unit
            override fun onActivityPaused(current: Activity) = Unit
            override fun onActivityStopped(current: Activity) = Unit
            override fun onActivitySaveInstanceState(current: Activity, state: android.os.Bundle) = Unit
        }
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        handle.setOnDismissListener { activity.application.unregisterActivityLifecycleCallbacks(callbacks); dismissed() }
    }

    private fun pickGame(activity: Activity, onPicked: (String, String) -> Unit) =
        AppPicker.show(activity, "Add a game", "Pick the game to map the triggers for.",
            AppPicker.Mode.Single(null) { onPicked(it.packageName, it.label) },
            filters = listOf(AppFilter.GAMES, AppFilter.ALL, AppFilter.SELECTED),
            selectedTag = "Added",
            marked = NativeTgkStorage.readProfiles(activity).map { it.packageName }.toSet())
}


package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.parts.*
import com.elitedarkkaiser.redmagic.showGamePickerDialogUI
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

internal object DevicePartsUi {
    fun hardwareCards(activity: Activity) = listOf(
        IconifyGrid.Item("Performance mode", "Thermal and clock profiles", Icons.PERF,
            status = { PartsPrefs.profile(activity).label }) { performance(activity) },
        IconifyGrid.Item("Charge separation", "Run from the charger with the battery idle", Icons.CHARGING,
            status = { PartsPrefs.separation(activity).label }) { separation(activity) }
    )
    fun softwareCards(activity: Activity) = listOf(
        IconifyGrid.Item("Ambient display", "Tap, lift and always-on schedule", Icons.DENSITY,
            status = { listOf(PartsPrefs.TAP to "Tap", PartsPrefs.LIFT to "Lift", PartsPrefs.SCHEDULE to "Schedule")
                .filter { PartsPrefs.bool(activity, it.first) }.joinToString(" · ") { it.second }.ifEmpty { "Off" } }) { ambient(activity) }
    )
    private fun body(c: Context, text: String): TextView = TextView(c).apply {
        this.text = text
        val m3 = M3(c); m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        setPadding(m3.dp(20), m3.dp(8), m3.dp(20), m3.dp(12))
    }
    private fun show(activity: Activity, title: String, content: LinearLayout) {
        content.addView(M3Dialog.title(activity, title), 0)
        val m3 = M3(activity); m3.connectRows(content); m3.tintWidgets(content)
        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.scroll(activity, content), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        M3Dialog.show(activity, panel)
    }
    private fun live(view: View, render: () -> Unit) {
        val tick = object : Runnable { override fun run() { render(); view.postDelayed(this, 1500) } }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { view.post(tick) }
            override fun onViewDetachedFromWindow(v: View) { view.removeCallbacks(tick) }
        })
        render()
    }
    private fun toggle(c: Context, m3: M3, key: String, title: String, detail: String, glyph: String): View {
        val switch = MaterialSwitch(c).apply {
            isChecked = PartsPrefs.bool(c, key)
            setOnCheckedChangeListener { _, checked -> PartsPrefs.set(c, key, checked) }
        }
        return m3.row(title, supporting = detail, glyph = glyph, trailing = switch)
    }
    private fun readings(): List<String> = PartsRuntime.temperatures.split(" · ")

    private fun statusDetail(): String? = PartsRuntime.status.takeIf { it != "Running" && it != "Off" }

    fun performance(activity: Activity) {
        val m3 = M3(activity)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val hero = FeatureHero(activity, Icons.PERF, CardShape.SOFT_BURST)
        content.addView(hero)
        val icons = mapOf(
            PerformanceProfile.BALANCED to Icons.BALANCED,
            PerformanceProfile.PERFORMANCE to Icons.GAUGE_HIGH,
            PerformanceProfile.EXTREME to Icons.EXPERIMENTAL,
            PerformanceProfile.ULTRA to Icons.TURBO
        )
        val detail = TextView(activity).apply {
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(0, m3.dp(M3.Space.md), 0, 0)
        }
        fun describe(profile: PerformanceProfile) { detail.text = profile.detail }
        content.addView(m3.block("Profile", Icons.CONFIGURE).apply {
            addView(ChoiceGroup(activity, PerformanceProfile.entries.map { Choice(it.label, it, icons[it]) },
                PartsPrefs.profile(activity), ChoiceGroup.Style.SHAPES) { profile ->
                PartsPrefs.set(activity, PartsPrefs.PROFILE, profile.key)
                describe(profile)
            }.view)
            addView(detail)
        })
        describe(PartsPrefs.profile(activity))
        content.addView(pageNote(activity, "Raised modes keep the fan running; Extreme and Ultra run it at full speed."))
        content.addView(pageNote(activity, "A hot battery drops back to Balanced, and clock floors reset while the screen is off.",
            Icons.TEMPERATURE))
        content.addView(pageNote(activity, "If Magic Key is set to Performance mode, the profile applies only while the slider is on.",
            Icons.MAGIC_KEY))
        live(content) { hero.set(PartsRuntime.performance, statusDetail(), readings()) }
        show(activity, "Performance mode", content)
    }

    fun ambient(activity: Activity) {
        val m3 = M3(activity)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val hero = FeatureHero(activity, Icons.MOON, CardShape.FLOWER)
        content.addView(hero)
        content.addView(m3.groupHeader("When always-on display is off"))
        content.addView(toggle(activity, m3, PartsPrefs.TAP, "Tap to show", "Tap the dark screen to see the clock", Icons.TAP))
        content.addView(toggle(activity, m3, PartsPrefs.LIFT, "Lift to show", "Pick up the phone to see the clock", Icons.PHONE_LIFT))

        lateinit var section: SwitchSection
        val scheduleSwitch = MaterialSwitch(activity).apply {
            isChecked = PartsPrefs.bool(activity, PartsPrefs.SCHEDULE)
            setOnCheckedChangeListener { _, checked ->
                PartsPrefs.set(activity, PartsPrefs.SCHEDULE, checked)
                section.setExpanded(checked)
            }
        }
        section = m3.sectionWithSwitch("Always-on schedule", scheduleSwitch, Icons.CLOCK,
            "Turn always-on display off overnight")
        val times = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, m3.dp(M3.Space.sm), 0, 0)
        }
        listOf(PartsPrefs.START to "Off from", PartsPrefs.END to "Back on at").forEachIndexed { index, (key, label) ->
            times.addView(timeTile(activity, m3, key, label), LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = m3.dp(M3.Space.sm) })
        }
        section.addView(times)
        section.addView(pageNote(activity, "Equal times leave always-on display alone. The schedule only turns it back on if the schedule turned it off."))
        section.setExpanded(scheduleSwitch.isChecked, animate = false)
        content.addView(section)
        live(content) {
            val paused = PartsPrefs.bool(activity, PartsPrefs.SUSPENDED)
            val on = listOf(PartsPrefs.TAP to "Tap", PartsPrefs.LIFT to "Lift", PartsPrefs.SCHEDULE to "Schedule")
                .filter { PartsPrefs.bool(activity, it.first) }.joinToString(" · ") { it.second }
            hero.set(PartsRuntime.ambient,
                if (paused) "Always-on display paused by the schedule"
                else if (on.isEmpty()) "Nothing turned on" else "On: $on")
        }
        show(activity, "Ambient display", content)
    }

    /** A large tappable time, for the schedule's two ends. */
    private fun timeTile(activity: Activity, m3: M3, key: String, label: String): View {
        val time = TextView(activity).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
            setTextColor(m3.onSurface)
        }
        fun refresh() {
            val minutes = PartsPrefs.minutes(activity, key)
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            }
            time.text = DateFormat.getTimeFormat(activity).format(calendar.time)
        }
        refresh()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = m3.filled(m3.wellSurface, 20, m3.onSurface, card = true)
            setPadding(m3.dp(16), m3.dp(14), m3.dp(16), m3.dp(14))
            addView(TextView(activity).apply {
                text = label
                m3.styleText(this, M3.Type.labelLarge, m3.onSurfaceVariant)
            })
            addView(time)
            isClickable = true
            contentDescription = label
            m3.springPress(this)
            setOnClickListener {
                val minutes = PartsPrefs.minutes(activity, key)
                TimePickerDialog(activity, { _, hour, minute ->
                    PartsPrefs.set(activity, key, hour * 60 + minute); refresh()
                }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(activity)).show()
            }
        }
    }

    fun separation(activity: Activity) {
        val m3 = M3(activity)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val hero = FeatureHero(activity, Icons.CHARGE_STATION, CardShape.COOKIE_4)
        content.addView(hero)
        val short = mapOf(SeparationMode.OFF to "Off", SeparationMode.GAMING to "In games", SeparationMode.ALWAYS to "Always")
        val icons = mapOf(SeparationMode.OFF to Icons.DISABLED, SeparationMode.GAMING to Icons.GAME_MODE,
            SeparationMode.ALWAYS to Icons.CHARGING)
        val detail = TextView(activity).apply {
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(0, m3.dp(M3.Space.md), 0, 0)
        }
        fun describe(mode: SeparationMode) {
            detail.text = if (mode == SeparationMode.OFF) "The battery charges as usual." else mode.label
        }
        content.addView(m3.block("Bypass the battery", Icons.BATTERY).apply {
            addView(ChoiceGroup(activity, SeparationMode.entries.map { Choice(short.getValue(it), it, icons[it]) },
                PartsPrefs.separation(activity)) { mode ->
                PartsPrefs.set(activity, PartsPrefs.SEPARATION, mode.key)
                describe(mode)
            }.view)
            addView(detail)
        })
        describe(PartsPrefs.separation(activity))
        content.addView(pageNote(activity, "The charger powers the phone while the battery rests, so it stays cooler."))
        content.addView(pageNote(activity, "Below 20% the battery charges normally; bypass resumes above 25%.", Icons.BATTERY))
        content.addView(pageNote(activity, "Charging stays on for 15 seconds after plugging in so the charger can settle its power.",
            Icons.CLOCK))
        live(content) {
            val parts = PartsRuntime.charge.split(" · ")
            hero.set(parts.first(), statusDetail(), parts.drop(1) + readings().drop(1))
        }
        show(activity, "Charge separation", content)
    }

    fun fanOptions(c: Context, manual: MaterialSwitch): View {
        val m3 = M3(c)
        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        var rendering = false
        val manualMirror = MaterialSwitch(c).apply {
            isChecked = manual.isChecked
            setOnCheckedChangeListener { _, on -> if (!rendering) manual.isChecked = on }
        }
        content.addView(m3.row("Manual fan", supporting = "Keep the fan running independently of the automatic options", glyph = Icons.FAN, trailing = manualMirror))
        content.addView(toggle(c, m3, PartsPrefs.GAMES, "Turn on in games", "Run the fan for apps in Game Space or your saved games", Icons.TRIGGERS))
        content.addView(toggle(c, m3, PartsPrefs.FAST, "Turn on while fast charging", "Start at 15 W or more; keeps cooling with the screen off", Icons.CHARGING))
        content.addView(m3.row("Select games", supporting = "Game Space games are included automatically", glyph = Icons.TRIGGERS, onClick = {
            showGamePickerDialogUI(c) { PartsRuntime.sync(c) }
        }))
        val speed = ComposeSlider(c, PartsPrefs.fanLevel(c).toFloat(), 1f..5f, 3,
            onValueChange = { value -> PartsPrefs.set(c, PartsPrefs.FAN_LEVEL, value.toInt()) })
        content.addView(m3.block("Automatic fan speed").apply { addView(speed.view) })
        val status = body(c, ""); content.addView(status)
        live(content) {
            rendering = true; manualMirror.isChecked = manual.isChecked; rendering = false
            status.text = if (CoolingOverrides.floor > 0) "Automatic cooling · minimum level ${CoolingOverrides.floor}" else "Automatic cooling is idle"
        }
        m3.connectRows(content)
        return content
    }
    fun fanMaster(c: Context, old: IconifyGrid.Master): IconifyGrid.Master = IconifyGrid.Master(
        read = { old.read() || PartsPrefs.bool(c, PartsPrefs.GAMES) || PartsPrefs.bool(c, PartsPrefs.FAST) || CoolingOverrides.floor > 0 },
        write = { on ->
            if (!on) { PartsPrefs.set(c, PartsPrefs.GAMES, false); PartsPrefs.set(c, PartsPrefs.FAST, false) }
            old.write(on)
        }, enabled = old.enabled)
}

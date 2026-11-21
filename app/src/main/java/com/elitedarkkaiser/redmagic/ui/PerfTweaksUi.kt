package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.elitedarkkaiser.redmagic.PerfTweaks
import com.elitedarkkaiser.redmagic.PerfTweaksPrefs
import com.google.android.material.materialswitch.MaterialSwitch

/** Cache Cleaner and GMS Optimizer, opened from the Hardware tab. */
object PerfTweaksUi {

    private const val PREFS = "perf_mods"
    private const val KEY_LAST_CLEAN = "last_clean_at"

    fun show(activity: Activity) {
        val m3 = M3(activity)
        val context: Context = activity

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(M3Dialog.header(context, "Performance mods", glyph = Icons.PERF))
            addView(cleanerCard(context, m3))
            addView(m3.groupHeader("Google Play Services"))
            addView(rootSwitchRow(
                context, m3,
                title = "GMS Optimizer",
                supporting = "Stop Google Play Services running in the background",
                glyph = Icons.GMS,
                read = { PerfTweaksPrefs.getBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER) },
                write = { on ->
                    PerfTweaksPrefs.setBoolean(context, PerfTweaksPrefs.KEY_GMS_OPTIMIZER, on)
                    PerfTweaks.setGmsOptimizer(context, on)
                },
                refusedMessage = "Couldn't change the GMS Optimizer"
            ))
            addView(warning(context, m3,
                "While it's on, notifications, account sync and Find My Device may stop working. " +
                    "Turning it off undoes most of the changes."))
        }

        val panel = M3Dialog.panel(activity).apply {
            addView(M3Dialog.scroll(activity, content), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        m3.connectRows(content)
        m3.tintWidgets(content)
        M3Dialog.show(activity, panel)
    }

    /** Cache Cleaner as one card: what it does, when it last ran, and a full-width button. */
    private fun cleanerCard(context: Context, m3: M3): View {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hero = FeatureHero(context, Icons.BROOM, CardShape.SUNNY)
        fun render(note: String? = null) {
            val last = prefs.getLong(KEY_LAST_CLEAN, 0L)
            val ago = if (last == 0L) "Never run" else "Last run " + android.text.format.DateUtils
                .getRelativeTimeSpanString(last, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
            hero.set("Cache Cleaner", note ?: "Trims storage and clears every app's cache.", listOf(ago))
        }
        render()
        lateinit var button: android.widget.Button
        button = M3Dialog.filledButton(context, "Clean now") {
            button.isEnabled = false
            button.text = "Cleaning…"
            Thread {
                val ok = runCatching { PerfTweaks.cleanCache() }.getOrDefault(false)
                button.post {
                    button.isEnabled = true
                    button.text = "Clean now"
                    if (ok) prefs.edit().putLong(KEY_LAST_CLEAN, System.currentTimeMillis()).apply()
                    render(if (ok) "Caches cleared." else "Couldn't clean the cache. Check root access.")
                }
            }.start()
        }
        hero.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(52))
            .apply { topMargin = m3.dp(M3.Space.md) })
        return hero
    }

    /** The GMS Optimizer's caveat, in the error container so it is read before the switch is. */
    private fun warning(context: Context, m3: M3, text: String): View = LinearLayout(context).apply {
        gravity = android.view.Gravity.TOP
        background = m3.cardShape(m3.errorContainer, M3.Metrics.groupCorner)
        setPadding(m3.dp(16), m3.dp(14), m3.dp(16), m3.dp(14))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = m3.dp(M3.Space.sm) }
        addView(TextView(context).apply {
            this.text = Icons.WARNING
            m3.styleGlyph(this, 16f, m3.onErrorContainer)
        }, LinearLayout.LayoutParams(m3.dp(24), m3.dp(24)).apply { marginEnd = m3.dp(12) })
        addView(TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.onErrorContainer)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** A row with a switch bound to root shell reads/writes, off the main thread. */
    private fun rootSwitchRow(
        context: Context,
        m3: M3,
        title: String,
        supporting: String,
        glyph: String,
        read: () -> Boolean,
        write: (Boolean) -> Unit,
        refusedMessage: String
    ): LinearLayout {
        val switch = MaterialSwitch(context)
        val row = m3.row(
            title = title,
            supporting = supporting,
            glyph = glyph,
            trailing = switch
        )

        lateinit var bind: (Boolean) -> Unit
        bind = { checked ->
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = checked
            switch.setOnCheckedChangeListener { _, wanted ->
                switch.isEnabled = false
                Thread {
                    val ok = runCatching { write(wanted) }.isSuccess
                    row.post {
                        switch.isEnabled = true
                        if (!ok) {
                            bind(!wanted)
                            Toast.makeText(context, refusedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }
        bind(read())
        return row
    }
}

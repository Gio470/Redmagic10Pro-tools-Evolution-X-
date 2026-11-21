package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

/** Root controls for installed software. */
object SoftwareTabUi {
    internal fun decorate(activity: Activity, item: IconifyGrid.Item): IconifyGrid.Item = when (item.title) {
        "Ambient display" -> item.copy(group = "Display", order = 0, wide = true,
            shape = CardShape.FLOWER)
        "Display density" -> item.copy(group = "Display", order = 1, wide = true,
            shape = CardShape.COOKIE_9, status = { "${activity.resources.configuration.densityDpi} dpi" })
        "Performance mods" -> item.copy(group = "Optimization", order = 10, wide = true,
            shape = CardShape.SOFT_BURST, tertiary = true, extra = { perfExtra(it) })
        else -> item.copy(group = "System", order = 20, shape = CardShape.PENTAGON, tertiary = true)
    }

    private fun additional(activity: Activity): List<IconifyGrid.Item> = (listOf(
        SectionPages.item(activity, SectionPages.entry(DisplayDensityUi.card(activity))),
        IconifyGrid.Item("Performance mods", "Cache Cleaner and GMS Optimizer", Icons.PERF) {
            PerfTweaksUi.show(activity)
        }
    ) + DevicePartsUi.softwareCards(activity)).map { decorate(activity, it) }

    data class Result(val view: LinearLayout)

    fun create(deps: SoftwareTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)

        container.addView(IconifyGrid.build(context, additional(deps.activity())))
        m3.connectRows(container)
        m3.tintWidgets(container)
        return Result(view = container)
    }

    /** Cache Cleaner and GMS Optimizer as two tiles, each with its own line. */
    private fun perfExtra(context: Context): android.view.View {
        val m3 = M3(context)
        val cleaner = CardExtras.tile(context, "Cache Cleaner", "Clears app caches")
        val gms = CardExtras.tile(context, "GMS Optimizer", "")
        return object : LinearLayout(context), LiveExtra {
            override fun refresh() {
                val on = com.elitedarkkaiser.redmagic.PerfTweaksPrefs.getBoolean(
                    context, com.elitedarkkaiser.redmagic.PerfTweaksPrefs.KEY_GMS_OPTIMIZER)
                CardExtras.setTileLine(gms, if (on) "On" else "Off")
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cleaner, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(gms, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = m3.dp(6) })
            refresh()
        }
    }
}

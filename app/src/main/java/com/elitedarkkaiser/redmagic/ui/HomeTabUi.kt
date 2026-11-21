package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.SystemStats

/** Live readings in the same grouped, expressive cards as Hardware and Software. */
object HomeTabUi {
    data class Refs(
        val compat: CompatStrip,
        /** Rebuilds only when access changes, retaining all live readings and expanded details. */
        val setAvailability: (rooted: Boolean, isPro: Boolean) -> Unit,
        val tempGauge: ArcGauge,
        val fanGauge: ArcGauge,
        val cpuUsageValue: TextView,
        val cpuSparkline: Sparkline,
        val cpuDetail: TextView,
        val cpuClusters: LinearLayout,
        val ramValue: TextView,
        val ramBar: UsageBar,
        val ramDetail: TextView,
        val swapValue: TextView,
        val swapBar: UsageBar
    )

    data class Result(val view: LinearLayout, val refs: Refs)

    fun create(deps: HomeTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)
        val compat = CompatStrip(context, m3).apply {
            onRecheck = { deps.recheckDeviceCompatibility() }
        }
        val isPro = deps.isRedMagic10Pro()
        val rooted = deps.hasRootAccess()
        compat.setDevice(isPro, if (isPro) "RedMagic 10 Pro" else deps.deviceCompatModelSummary())
        if (rooted) compat.setRoot(true, "Superuser permission granted")

        val tempGauge = ArcGauge(context).apply { set(0f, "--", "Temperature") { it.onSurfaceVariant } }
        val fanGauge = ArcGauge(context).apply { set(0f, "--", "RPM") { it.onSurfaceVariant } }
        val cooling = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(gauge(context, m3, "Temperature", tempGauge), LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(gauge(context, m3, "Fan", fanGauge), LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = m3.dp(12) })
        }

        val cpuUsageValue = reading(context, m3)
        val cpuSparkline = Sparkline(context)
        val cpuDetail = TextView(context)
        val cpuClusters = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val processor = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(cpuUsageValue)
            addView(cpuSparkline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(64))
                .apply { topMargin = m3.dp(8) })
            cpuSparkline.contentDescription = "Recent processor load"
            addView(cpuDetail.apply {
                text = "--"
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                setPadding(0, m3.dp(12), 0, 0)
            })
            addView(M3Dialog.textButton(context, "Show CPU details") {
                val show = cpuClusters.visibility != View.VISIBLE
                cpuClusters.visibility = if (show) View.VISIBLE else View.GONE
                (cpuClusters.tag as? android.widget.Button)?.text =
                    if (show) "Hide CPU details" else "Show CPU details"
            }.also { cpuClusters.tag = it })
            addView(cpuClusters)
        }

        val ramValue = reading(context, m3)
        val ramBar = UsageBar(context)
        val ramDetail = TextView(context)
        val swapValue = TextView(context)
        val swapBar = UsageBar(context) { it.tertiary }
        val memory = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(ramValue)
            addView(ramBar)
            addView(ramDetail.apply {
                text = "--"
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                setPadding(0, m3.dp(M3.Space.xs), 0, 0)
            })
            addView(meterRow(context, m3, "Swap", swapValue, swapBar))
        }

        // Grid rebuilds replace card shells, while the live views keep their values and state.
        fun reuse(view: View): (Context) -> View = {
            (view.parent as? ViewGroup)?.removeView(view)
            view
        }
        fun items(hasRoot: Boolean, supported: Boolean): List<IconifyGrid.Item> = buildList {
            add(IconifyGrid.Item("System status", null, Icons.SHIELD, group = "Device", hero = true,
                shape = CardShape.COOKIE_9, status = { compat.summary }, extra = reuse(compat)))
            if (hasRoot && supported) add(IconifyGrid.Item("Cooling", "Temperature and fan speed", Icons.FAN,
                group = "Live readings", order = 10, wide = true, shape = CardShape.COOKIE_12,
                extra = reuse(cooling), onClick = { deps.openTab("hardware") }))
            if (hasRoot) {
                add(IconifyGrid.Item("Processor", "Load and CPU clocks", Icons.HARDWARE,
                    group = "Live readings", order = 11, wide = true, shape = CardShape.SOFT_BURST,
                    tertiary = true, extra = reuse(processor)))
                add(IconifyGrid.Item("Memory", "RAM and swap", Icons.MEMORY,
                    group = "Live readings", order = 12, wide = true, shape = CardShape.CLOVER,
                    extra = reuse(memory)))
            }
        }
        val grid = IconifyGrid.build(context, items(rooted, isPro), chips = listOf(
            IconifyGrid.Chip(Icons.SHIELD) { compat.verificationCount }
        ))
        container.addView(grid)
        var availability = rooted to isPro
        val setAvailability: (Boolean, Boolean) -> Unit = { hasRoot, supported ->
            if (availability != (hasRoot to supported)) {
                availability = hasRoot to supported
                IconifyGrid.fill(grid, items(hasRoot, supported))
            }
        }
        return Result(container, Refs(compat, setAvailability, tempGauge, fanGauge, cpuUsageValue,
            cpuSparkline, cpuDetail, cpuClusters, ramValue, ramBar, ramDetail, swapValue, swapBar))
    }

    private fun reading(context: Context, m3: M3) = TextView(context).apply {
        text = "--"
        m3.styleText(this, M3.Type.headlineLarge, m3.onSurface)
    }

    private fun gauge(context: Context, m3: M3, title: String, gauge: ArcGauge) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            m3.styleText(this, M3.Type.titleSmall, m3.onSurfaceVariant)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(gauge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(128)))
    }

    // ---- Live updates, kept here so the views and what writes to them stay in one file ---------

    /**
     * How hot, as a position between comfortable and worth worrying about.
     *
     * The range is Fahrenheit because that is what the hardware read returns; what the gauge prints
     * is whatever the user's unit setting formatted. 86F is a phone doing nothing and 122F is one
     * throttling, which puts the interesting part of the range across the middle of the dial rather
     * than bunched at one end.
     */
    fun applyTemperature(gauge: ArcGauge, tempF: Float?, display: String) {
        if (tempF == null) {
            gauge.set(0f, "--", "Temperature") { it.onSurfaceVariant }
            return
        }
        val fraction = ((tempF - TEMP_MIN_F) / (TEMP_MAX_F - TEMP_MIN_F)).coerceIn(0f, 1f)
        val caption = when {
            tempF >= TEMP_HOT_F -> "Hot"
            tempF >= TEMP_WARM_F -> "Warm"
            else -> "Cool"
        }
        gauge.set(fraction, display, caption) { m3 ->
            when {
                tempF >= TEMP_HOT_F -> m3.error
                tempF >= TEMP_WARM_F -> m3.tertiary
                else -> m3.primary
            }
        }
    }

    /**
     * The fan, as its level rather than its RPM.
     *
     * RPM is the number worth printing and the wrong one to draw an arc from: nothing on the phone
     * says what the maximum is, so a dial scaled to a guess would be a dial that lies. The level is
     * bounded by the node that sets it, so the arc is honest and the RPM sits in the middle of it.
     */
    fun applyFan(gauge: ArcGauge, level: Int?, rpm: Int?, enabled: Boolean) {
        if (!enabled || level == null) {
            gauge.set(0f, "--", "Off") { it.onSurfaceVariant }
            return
        }
        gauge.set(
            level.coerceIn(0, FAN_MAX_LEVEL) / FAN_MAX_LEVEL.toFloat(),
            rpm?.toString() ?: "--",
            "RPM"
        ) { it.primary }
    }

    /**
     * One cluster: how many cores, what they are clocked at, and how close that is to their
     * ceiling. The bar is what makes a row of frequencies readable -- "1.9 GHz" says nothing about
     * whether that is a core asleep or a core flat out without its maximum beside it, and even then
     * it is arithmetic the bar has already done.
     */
    fun clusterRow(context: Context, m3: M3, index: Int, cluster: SystemStats.CpuCluster): LinearLayout {
        val title = TextView(context).apply {
            // The kernel groups cores by their maximum clock, so cluster 0 is the fastest one --
            // "big"/"little" is the shape of that, not something the kernel actually labels.
            text = "Cluster $index · ${cluster.cores} core${if (cluster.cores == 1) "" else "s"}"
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurface)
        }
        val value = TextView(context).apply {
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        }
        val bar = UsageBar(context) { it.secondary }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(M3.Space.sm), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                addView(value)
            })
            addView(bar)
            updateClusterRow(this, cluster)
        }
    }

    /** Writes a cluster's live figures into a row [clusterRow] built. */
    fun updateClusterRow(row: LinearLayout, cluster: SystemStats.CpuCluster) {
        val header = row.getChildAt(0) as? LinearLayout ?: return
        (header.getChildAt(1) as? TextView)?.text =
            "${SystemStats.formatFreq(cluster.currentKHz)} / ${SystemStats.formatFreq(cluster.maxKHz)}"
        val percent = if (cluster.maxKHz > 0L) {
            (cluster.currentKHz * 100 / cluster.maxKHz).toInt()
        } else {
            0
        }
        (row.getChildAt(1) as? UsageBar)?.setPercent(percent)
    }

    /** A secondary reading inside a card: its name and figure on a row, its bar beneath. */
    private fun meterRow(
        context: Context,
        m3: M3,
        title: String,
        valueView: TextView,
        bar: UsageBar
    ): LinearLayout {
        if (valueView.text.isNullOrEmpty()) valueView.text = "--"
        m3.styleText(valueView, M3.Type.bodyMedium, m3.onSurfaceVariant)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(M3.Space.md), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = title
                    m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(valueView)
            })
            addView(bar)
        }
    }

    private const val TEMP_MIN_F = 86f
    private const val TEMP_MAX_F = 131f
    private const val TEMP_WARM_F = 104f
    private const val TEMP_HOT_F = 118f

    /** What the fan's level node accepts, which is what the Cooling slider spans. */
    private const val FAN_MAX_LEVEL = 5
}


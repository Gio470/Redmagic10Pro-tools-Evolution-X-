package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date

/** Device and root checks, with a neutral pending state and guidance when one needs attention. */
class CompatStrip(
    context: Context,
    private val m3: M3
) : LinearLayout(context) {
    private class Check(val name: String, val glyph: String) {
        var ok: Boolean? = null
        var detail = "Checking…"
        lateinit var mark: TextView
        lateinit var description: TextView
        lateinit var state: TextView
    }
    private val device = Check("Device", Icons.HARDWARE)
    private val root = Check("Root access", Icons.SHIELD)
    private val checks = listOf(device, root)
    private val supporting = TextView(context)
    private val details = LinearLayout(context).apply { orientation = VERTICAL }
    private val readyChips = LinearLayout(context)
    private val checkedAt = TextView(context)
    private val recheck: Button
    var onRecheck: (() -> Unit)? = null
    val isReady: Boolean get() = checks.all { it.ok == true }
    var summary: String = ""
        private set
    val verificationCount: String get() = "${checks.count { it.ok == true }} of ${checks.size} verified"

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        m3.styleText(supporting, M3.Type.bodyMedium, m3.onSurfaceVariant)
        addView(supporting, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(4); bottomMargin = m3.dp(12) })
        checks.forEach { details.addView(checkRow(it)) }
        addView(details)
        listOf("10 Pro", "Root").forEachIndexed { index, label ->
            readyChips.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                background = m3.surfaceShape(m3.surfaceContainer, M3.Shape.full)
                setPadding(m3.dp(8), m3.dp(8), m3.dp(8), m3.dp(8))
                addView(TextView(context).apply {
                    text = Icons.CHECK
                    m3.styleGlyph(this, 11f, m3.primary)
                }, LayoutParams(m3.dp(16), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    text = label
                    m3.styleText(this, M3.Type.labelMedium, m3.onSurface)
                })
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { if (index > 0) marginStart = m3.dp(6) })
        }
        addView(readyChips)
        addView(checkedAt.apply {
            text = "Waiting for a live check"
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(12), 0, 0)
        })
        recheck = M3Dialog.tonalButton(context, "Recheck") {
            beginCheck()
            onRecheck?.invoke()
        }
        addView(recheck, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = m3.dp(8) })
        render()
    }

    fun setDevice(ok: Boolean, label: String) {
        device.ok = ok; device.detail = label; render()
    }

    fun setRoot(ok: Boolean, label: String) {
        root.ok = ok; root.detail = label; render()
    }

    fun beginCheck() {
        root.ok = null; root.detail = "Checking superuser access…"
        render()
    }

    /** All results from one live read land together. */
    fun applyStatus(isPro: Boolean, model: String, rooted: Boolean) {
        device.ok = isPro; device.detail = model
        root.ok = rooted; root.detail = if (rooted) "Superuser permission granted" else "Superuser permission is needed"
        checkedAt.text = "Checked at ${android.text.format.DateFormat.getTimeFormat(context).format(Date())}"
        render()
    }

    private fun render() {
        val ready = isReady
        val attention = checks.any { it.ok == false }
        val pending = !ready && !attention
        val title = when {
            ready -> "Ready for control"
            device.ok == false -> "Device not supported"
            root.ok == false -> "Root access needed"
            else -> "Checking your phone"
        }
        summary = title
        supporting.text = when {
            ready -> "Your phone and root access are verified."
            device.ok == false -> "Hardware controls are made for the RedMagic 10 Pro."
            root.ok == false -> "Grant root to use live readings and hardware controls."
            else -> "Checking your device and root access."
        }
        details.visibility = if (ready) GONE else VISIBLE
        readyChips.visibility = if (ready) VISIBLE else GONE
        recheck.isEnabled = !pending
        checks.forEach { check ->
            val color = when (check.ok) { true -> m3.primary; false -> m3.error; null -> m3.onSurfaceVariant }
            check.mark.setTextColor(color)
            check.description.text = check.detail
            check.state.text = when (check.ok) { true -> "Verified"; false -> "Check needed"; null -> "Waiting" }
            check.state.setTextColor(color)
        }
        contentDescription = "$title. " + checks.joinToString(". ") { "${it.name}: ${it.detail}" }
    }

    private fun checkRow(check: Check): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, m3.dp(10), 0, m3.dp(10))
        check.mark = TextView(context).apply {
            text = check.glyph
            gravity = Gravity.CENTER
            m3.styleGlyph(this, 16f, m3.onSurfaceVariant)
        }
        addView(check.mark, LayoutParams(m3.dp(32), m3.dp(32)).apply { marginEnd = m3.dp(10) })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(TextView(context).apply {
                text = check.name
                m3.styleText(this, M3.Type.titleSmall, m3.onSurface)
            })
            check.description = TextView(context).apply {
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                setPadding(0, m3.dp(2), 0, 0)
            }
            addView(check.description)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        check.state = TextView(context).apply {
            m3.styleText(this, M3.Type.labelSmall, m3.onSurfaceVariant)
            setPadding(m3.dp(8), 0, 0, 0)
        }
        addView(check.state)
    }
}

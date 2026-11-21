package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.MagicKeyMode
import com.elitedarkkaiser.redmagic.MagicKeyRuntime
import com.google.android.material.materialswitch.MaterialSwitch

internal object MagicKeyOptionsUi {
    fun create(activity: Activity): View {
        val m3 = M3(activity)
        val reverse = MaterialSwitch(activity).apply {
            isChecked = MagicKeyRuntime.reversed(activity)
            setOnCheckedChangeListener { _, checked -> MagicKeyRuntime.setReversed(activity, checked) }
        }
        val status = TextView(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(m3.row("Reverse slider direction", supporting = "Use the other position to activate the action",
                glyph = Icons.MAGIC_KEY, trailing = reverse))
            addView(m3.row("Magic Key status", supportingView = status, glyph = Icons.CONFIGURE))
        }
        fun refresh() {
            status.text = if (MagicKeyRuntime.mode(activity) == MagicKeyMode.DISABLED) "Off" else MagicKeyRuntime.status
        }
        val tick = object : Runnable {
            override fun run() { refresh(); content.postDelayed(this, 1000) }
        }
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { content.post(tick) }
            override fun onViewDetachedFromWindow(v: View) { content.removeCallbacks(tick) }
        })
        m3.connectRows(content)
        refresh()
        return content
    }
}

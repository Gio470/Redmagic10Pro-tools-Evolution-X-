package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Shared informational block used by the trigger setup pages. */
object IconifyKit {
    /**
     * Iconify's info block: a 24dp info icon, 12dp of air, then the text -- the first line in the
     * content colour, anything after it at 75%. Returns the block and the text view, so a line
     * that changes (a count, a status) can be rewritten in place.
     */
    fun infoBlock(context: Context, text: CharSequence = ""): Pair<LinearLayout, TextView> {
        val m3 = M3(context)
        val body = TextView(context).apply {
            this.text = text
            m3.styleText(this, M3.Type.bodyMedium, m3.secondaryText(m3.onSurface))
        }
        val block = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(8), m3.dp(16), m3.dp(8), m3.dp(16))
            addView(m3.iconChip(Icons.INFO), LinearLayout.LayoutParams(m3.dp(M3.Metrics.iconBox), m3.dp(M3.Metrics.iconBox)))
            addView(body, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = m3.dp(12) })
        }
        return block to body
    }
}

package com.elitedarkkaiser.redmagic.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The Hardware and Software tabs' cards, laid out the Material 3 Expressive way: grouped under
 * labels ("Cooling", "Controls", ...), each group a connected block of cards -- large corners on
 * the outside, small ones where cards meet -- with wide cards taking a whole row and the rest
 * paired two to a row. A row of live chips sits at the top: how many features are on, and
 * whatever else the tab passes in.
 */
object IconifyGrid {

    /** The feature's own on/off state, when the card stands for something that has one. */
    class Master(
        val read: () -> Boolean,
        val write: (Boolean) -> Unit,
        /** Whether the real switch would take a tap right now; the mirror greys out when not. */
        val enabled: () -> Boolean = { true }
    )

    data class Item(
        val title: String,
        val subtitle: String?,
        val glyph: String,
        val master: Master? = null,
        /** The label of the group the card sits in; null for an unlabelled group. */
        val group: String? = null,
        /** Where it sorts: groups follow their first card's order. */
        val order: Int = 0,
        val shape: CardShape = CardShape.COOKIE_9,
        /** The tertiary accent instead of the primary, for a little variety between groups. */
        val tertiary: Boolean = false,
        /** Takes a whole row. */
        val wide: Boolean = false,
        /** A whole row, a larger icon and title: the tab's lead feature. */
        val hero: Boolean = false,
        /** What the feature is doing right now, in a few words; falls back to [subtitle]. */
        val status: (() -> String?)? = null,
        /** Anything more a wide card shows under its title: a meter, chips, a bar. */
        val extra: ((Context) -> View)? = null,
        /** Last, so a trailing lambda is the click. */
        val onClick: (() -> Unit)? = null
    )

    /** A live chip for the row at the top; hidden while [text] has nothing to say. */
    data class Chip(val glyph: String, val text: () -> String?)

    /** The grid itself, which keeps its chips so [fill] can lay it out again. */
    @SuppressLint("ViewConstructor")
    class Grid(context: Context, val chips: List<Chip>) : LinearLayout(context) {
        internal var items: List<Item> = emptyList()
        internal val chipViews = mutableListOf<Pair<View, () -> String?>>()

        private val ticker = object : Runnable {
            override fun run() {
                if (isShown) refreshChips()
                handler?.postDelayed(this, 1500L)
            }
        }

        internal fun refreshChips() {
            chipViews.forEach { (view, text) ->
                val value = runCatching { text() }.getOrNull()
                view.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
                if (value != null) CardExtras.setChipText(view, value)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            refreshChips()
            postDelayed(ticker, 1500L)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }
    }

    fun build(context: Context, items: List<Item>, topMarginDp: Int = 0,
        chips: List<Chip> = emptyList()): LinearLayout {
        val m3 = M3(context)
        return Grid(context, chips).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = m3.dp(topMarginDp)
                bottomMargin = m3.dp(8)
            }
            fill(this, items)
        }
    }

    /**
     * Lays [grid] out again from [items], for a card that can only be ruled out once a root read
     * comes back: where every other card sits, and which corners it gets, follow from the list.
     */
    fun fill(grid: LinearLayout, items: List<Item>) {
        val context = grid.context
        val m3 = M3(context)
        grid.removeAllViews()
        val chips = (grid as? Grid)?.chips.orEmpty()
        (grid as? Grid)?.let { it.items = items; it.chipViews.clear() }

        // The chips: how many features are on, then the tab's own.
        val switched = items.mapNotNull { it.master }
        val allChips = buildList {
            if (switched.isNotEmpty()) add(Chip(Icons.POWER) {
                "${switched.count { m -> runCatching { m.read() }.getOrDefault(false) }} of ${switched.size} on"
            })
            addAll(chips)
        }
        if (allChips.isNotEmpty()) {
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                allChips.forEach { chip ->
                    val view = CardExtras.chip(context, "", glyph = chip.glyph, background = m3.surfaceContainerHigh)
                    addView(view, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = m3.dp(8) })
                    (grid as? Grid)?.chipViews?.add(view to chip.text)
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m3.dp(4); marginStart = m3.dp(2) })
            (grid as? Grid)?.refreshChips()
        }

        val sorted = items.withIndex().sortedWith(compareBy({ it.value.order }, { it.index })).map { it.value }
        val groups = LinkedHashMap<String?, MutableList<Item>>()
        sorted.forEach { groups.getOrPut(it.group) { mutableListOf() } += it }

        val outer = m3.dpF(28f)
        val inner = m3.dpF(8f)
        val gap = m3.dp(4)
        groups.entries.forEachIndexed { groupIndex, (label, members) ->
            if (label != null) grid.addView(TextView(context).apply {
                text = label.uppercase()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
                letterSpacing = 0.05f
                setTextColor(m3.primary)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = m3.dp(if (groupIndex == 0 && allChips.isEmpty()) 4 else 18)
                bottomMargin = m3.dp(9)
                marginStart = m3.dp(6)
            }) else if (groupIndex > 0) grid.addView(View(context), LinearLayout.LayoutParams(1, m3.dp(16)))

            // Rows: a wide card alone, the rest two by two; a half left over takes the whole row.
            val rows = mutableListOf<List<Item>>()
            var pending: Item? = null
            members.forEach { item ->
                if (item.wide || item.hero) {
                    pending?.let { rows += listOf(it) }
                    pending = null
                    rows += listOf(item)
                } else if (pending == null) {
                    pending = item
                } else {
                    rows += listOf(pending!!, item)
                    pending = null
                }
            }
            pending?.let { rows += listOf(it) }

            rows.forEachIndexed { r, row ->
                val top = r == 0
                val bottom = r == rows.size - 1
                val rowView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                }
                row.forEachIndexed { c, item ->
                    val left = c == 0
                    val right = c == row.size - 1
                    fun corner(a: Boolean, b: Boolean) = if (a && b) outer else inner
                    val tl = corner(top, left); val tr = corner(top, right)
                    val br = corner(bottom, right); val bl = corner(bottom, left)
                    val radii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
                    // A card alone on its row takes it whole, even if it was meant as a half.
                    val card = ExpressiveCard(context,
                        if (row.size == 1 && !item.wide && !item.hero) item.copy(wide = true) else item, radii)
                    rowView.addView(card, LinearLayout.LayoutParams(
                        0,
                        if (row.size == 1) ViewGroup.LayoutParams.WRAP_CONTENT else m3.dp(HALF_HEIGHT),
                        1f
                    ).apply { if (c > 0) marginStart = gap })
                }
                grid.addView(rowView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (r > 0) topMargin = gap })
            }
        }
    }

    private const val HALF_HEIGHT = 164
}

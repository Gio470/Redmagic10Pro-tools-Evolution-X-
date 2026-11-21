package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import java.util.WeakHashMap

/** A screen of the app, for the panels that used to be bottom sheets. */
object PageHost {

    private class Entry(val panel: Panel, val view: View, val title: String?)

    /**
     * Where pages are added: the frame holding the tab content, set by
     * [MainUiLauncher][com.elitedarkkaiser.redmagic.MainUiLauncher]. Weak, because it holds a
     * View -- an Activity on its way out must not be kept alive by this.
     */
    private val hosts = WeakHashMap<Activity, ViewGroup>()
    private val stacks = WeakHashMap<Activity, MutableList<Entry>>()
    private val backCallbacks = WeakHashMap<Activity, OnBackPressedCallback>()
    private val listeners = WeakHashMap<Activity, (String?) -> Unit>()

    fun attach(activity: Activity, host: ViewGroup) {
        hosts[activity] = host
    }

    /**
     * Told the name of the open page, or null when none is, every time that changes.
     *
     * This is what puts the bottom bar into back mode -- see
     * [BackGlassBar][com.elitedarkkaiser.redmagic.ui.glassbar.GlassBarScaffold]. The bar is the
     * way out of a page, so it has to know there is one.
     */
    fun onOpenPageChanged(activity: Activity, listener: (String?) -> Unit) {
        listeners[activity] = listener
        listener(topTitle(activity))
    }

    /** Backs out of the open page, for the bar's own back control. */
    fun popTop(activity: Activity) {
        val top = stacks[activity]?.lastOrNull() ?: return
        if (top.panel.cancelable) top.panel.cancel()
    }

    private fun topTitle(activity: Activity): String? = stacks[activity]?.lastOrNull()?.title

    private fun notify(activity: Activity) {
        listeners[activity]?.invoke(topTitle(activity))
    }

    fun push(activity: Activity, content: View, cancelable: Boolean): Panel {
        val host = hosts[activity]
            ?: activity.findViewById(android.R.id.content)
            ?: throw IllegalStateException("No host to put a page in")

        val m3 = M3(activity)
        val page = FrameLayout(activity).apply {
            // Draws nothing. The app's background is a Compose layer behind this whole View tree,
            // and an opaque fill here would cover it -- which is what made a sheet read as a
            // separate thing sitting on the app. What a page covers is the tab underneath, by
            // hiding it, so the background still shows through and a page reads as a tab.
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val panel = Panel(cancelable) { remove(activity, page) }

        val column = CoordinatorLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val title = hoistTitle(content)
        // Share one scrolling app bar across every detail page, including pages with their own
        // app list. Nested scrolling keeps the list virtualized and gives it the space released
        // by the title, without moving filters or controls into a second scrolling container.
        val appBar = AppBarLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            elevation = 0f
            stateListAnimator = null
            setLiftOnScroll(false)
            addView(titleBar(activity, m3, title), AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL })
        }
        column.addView(appBar, CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        // Pulled out before content goes into the column: see [ActionRow][M3Dialog.ActionRow] for
        // why a stacked row costs the scrollable content more than its own height, and
        // [pinActionRow] for where it goes instead. A panel with no such row is untouched.
        val actionRow = takeActionRow(content)
        // Short forms also need a nested-scrolling child so their heading follows the same
        // gesture. Existing list/scroller pages retain their original containers.
        val pageContent = if (M3Dialog.findUnderBar(content) == null)
            M3Dialog.scroll(activity, content) else content
        M3Dialog.findUnderBar(pageContent)?.isNestedScrollingEnabled = true
        // The scrolling behavior measures the content below the expanded heading and lets it
        // occupy the released space as the heading scrolls off screen.
        val fills = pageContent is NestedScrollView || pageContent is ScrollView
        column.addView(
            pageContent,
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { behavior = AppBarLayout.ScrollingViewBehavior() }
        )
        page.addView(column)
        if (actionRow != null) pinActionRow(page, actionRow, m3)

        // Where the room for the bar (and, when there is one, the pinned action row above it)
        // goes -- which decides whether the page floats over both or just sits above a reserved
        // strip.
        val scrolled = (pageContent as? ViewGroup)?.takeIf { fills }?.getChildAt(0)
        // A panel that is a few fixed controls over a list that scrolls on its own -- De-Bloat,
        // Processes -- marks that list (M3Dialog.scrollsUnderBar). With no action row to pin, it
        // gets the tabs' treatment too: the page stops reserving a strip at the bottom, the list
        // runs to the bottom edge of the screen, under the back bar and the navigation bar, and
        // its own bottom padding is the travel that lets its last row rise clear of both.
        val underBar = if (fills) null else M3Dialog.findUnderBar(pageContent)
        if (underBar != null) {
            pageContent.setPadding(pageContent.paddingLeft, pageContent.paddingTop, pageContent.paddingRight, 0)
            (underBar as? ViewGroup)?.clipToPadding = false
        }
        // When everything already fits under the expanded title, the title stays put and nothing
        // moves. Without this the app bar collapsed on any drag, so a page that needed no
        // scrolling still scrolled.
        val scroller = underBar ?: pageContent.takeIf { fills }
        (appBar.layoutParams as CoordinatorLayout.LayoutParams).behavior =
            FitAwareAppBarBehavior { contentFits(column, appBar, pageContent, scroller) }
        // Last known inset values, read by [recompute] whichever of the two things below calls it:
        // a fresh window inset (rotation, IME) or the action row's own first real measurement.
        // Neither is known at push() time, so both start at zero and correct once the real numbers
        // arrive -- a page opens under the bar's usual clearance for one frame at most.
        var cutoutTop = 0
        var barTop = m3.dp(BAR_TOP_DP)

        fun recompute() {
            // page's own padding is bar clearance ONLY, in both branches -- it is what a pinned
            // row's own gravity=BOTTOM position is measured against, so it cannot also carry the
            // row's height or the row ends up floating rowHeight too high, above its own clearance
            // rather than sitting on it. The row needs no margin of its own as a result: page's
            // padding already puts its bottom edge exactly barTop above the true bottom, which is
            // where the glass bar is.
            val side = m3.extraGutterPx()
            val travel = scrolled ?: underBar
            val rowRoom = if (actionRow != null) actionRow.height + m3.dp(ROW_GAP_DP) else 0
            page.setPadding(side, cutoutTop, side, if (travel != null) 0 else barTop)
            if (travel != null) {
                // Everything runs to the bottom edge; the row stands on the bar by its own margin,
                // and the scroller's padding is the travel that lifts its last line over both.
                column.setPadding(0, 0, 0, 0)
                actionRow?.let { row ->
                    (row.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        if (lp.bottomMargin != barTop) {
                            lp.bottomMargin = barTop
                            row.layoutParams = lp
                        }
                    }
                }
                travel.setPadding(
                    travel.paddingLeft, travel.paddingTop, travel.paddingRight,
                    // A little over the bar, so the last row does not sit flush against it.
                    barTop + rowRoom + if (underBar != null) m3.dp(M3.Space.md) else 0
                )
            } else {
                column.setPadding(0, 0, 0, rowRoom)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(page) { _, insets ->
            cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            barTop = m3.dp(BAR_TOP_DP) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            recompute()
            insets
        }
        // The row's real height is not known until its first layout pass, which recompute() above
        // may well have run ahead of (insets and the first layout race each other). Corrected once
        // more here rather than inside the insets callback itself, which runs again on every future
        // inset change and would otherwise stack a fresh listener on the row each time.
        var rowHeight = -1
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        actionRow?.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height != rowHeight) {
                rowHeight = v.height
                mainHandler.post { recompute() }
            }
        }

        val stack = stacks.getOrPut(activity) { mutableListOf() }
        // What this page stands in front of: the page below it, or -- for the first -- the tabs.
        cover(host, stack.lastOrNull()?.view, hidden = true)

        host.addView(page)
        // The window handed its insets out long before this page existed, and a view added
        // afterwards is not sent them again unless it asks -- without this the page never learnt
        // how tall the navigation bar is and kept only the back bar's clearance, leaving its
        // bottom rows under the navigation bar. It also starts from the insets the window has now,
        // so the very first frame is already right rather than corrected a dispatch later.
        ViewCompat.getRootWindowInsets(host)?.let { insets ->
            cutoutTop = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            barTop = m3.dp(BAR_TOP_DP) +
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }
        recompute()
        ViewCompat.requestApplyInsets(page)
        enter(page)

        stack.add(Entry(panel, page, title))
        installBackCallback(activity)
        backCallbacks[activity]?.isEnabled = true
        notify(activity)
        return panel
    }

    /** Takes a panel's trailing row of buttons out of it, if it has one. */
    private fun takeActionRow(content: View): View? {
        val column = content as? ViewGroup ?: return null
        val last = column.getChildAt(column.childCount - 1) ?: return null
        if (last.tag !== M3Dialog.ActionRow) return null
        column.removeView(last)
        return last
    }

    /**
     * Puts the row back over the page rather than in it, at the same shelf the bottom bar sits on.
     *
     * No background of its own: these are shaped, filled buttons, and what belongs behind them is
     * the page they act on, the same as the bar above them. [column]'s own gutter (16dp) is
     * repeated here so the buttons land on the same vertical lines as the content they came from.
     */
    private fun pinActionRow(page: FrameLayout, row: View, m3: M3) {
        page.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = m3.dp(16)
            rightMargin = m3.dp(16)
        })
    }

    private fun remove(activity: Activity, view: View) {
        val host = view.parent as? ViewGroup
        val stack = stacks[activity]
        stack?.removeAll { it.view === view }
        // Uncovered before the animation rather than after it, so what the page slides off is
        // already there rather than appearing once it has gone.
        if (host != null) {
            val revealed = stack?.lastOrNull()?.view
            cover(host, revealed, hidden = false)
            reveal(host, revealed)
        }
        exit(view) { host?.removeView(view) }
        backCallbacks[activity]?.isEnabled = stack?.isNotEmpty() == true
        notify(activity)
    }

    /**
     * Hides or restores what a page stands in front of.
     *
     * A null [target] means everything in the host that is not a page -- the tab content. The app's
     * background is a layer behind all of it and is never touched, which is what lets a page draw
     * nothing of its own.
     */
    private fun cover(host: ViewGroup, target: View?, hidden: Boolean) {
        val visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (target != null) {
            target.visibility = visibility
            return
        }
        val pages = stacks.values.flatten().map { it.view }
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (pages.none { it === child }) child.visibility = visibility
        }
    }

    /**
     * Re-registered on every push rather than installed once: the dispatcher runs the most
     * recently added enabled callback, so re-adding is what keeps Back going to whichever of a
     * page and an [Expander] was opened last.
     */
    private fun installBackCallback(activity: Activity) {
        val owner = activity as? ComponentActivity ?: return
        backCallbacks.remove(activity)?.remove()
        val callback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val top = stacks[activity]?.lastOrNull() ?: return
                if (top.panel.cancelable) top.panel.cancel()
            }
        }
        owner.onBackPressedDispatcher.addCallback(owner, callback)
        backCallbacks[activity] = callback
    }

    /** The page's own heading: the panel's name, where a tab puts its name. */
    private fun titleBar(activity: Activity, m3: M3, title: String?): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // The tab gutter, so the title lines up with the content under it.
            setPadding(m3.dp(M3.Space.md), m3.dp(M3.Space.xs), m3.dp(M3.Space.md), 0)
            if (title != null) {
                addView(TextView(activity).apply {
                    text = title
                    m3.styleText(this, M3.Type.headlineLarge, m3.onSurface)
                    // Level with a tab's own title (32dp of tab padding plus its 4dp), counting
                    // this bar's 8dp, so the title does not jump between a tab and a page.
                    setPadding(m3.dp(8), m3.dp(28), m3.dp(8), m3.dp(M3.Space.lg))
                })
            }
        }

    /**
     * Takes the panel's name out of its content, so the page can show it and the content will not.
     *
     * Every panel names itself at the top, through [M3Dialog.header] or [M3Dialog.title], and both
     * tag that view. Left alone it would sit a few dp under the page's own title saying the same
     * thing in a smaller size. Hidden rather than removed, so nothing that kept a reference to it
     * -- a header with a switch in its trailing slot -- finds it gone.
     */
    private fun hoistTitle(content: View): String? {
        val headline = findHeadline(content) ?: return null
        val text = headline.text?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val row = headline.parent as? View
        when (row?.tag) {
            // A header row of chip plus headline: all of it goes, chip included.
            M3Dialog.HeadlineRow -> row.visibility = View.GONE
            // A header row with a control in it stays put, and so does its headline -- lifting the
            // title out from over a switch would leave the switch explaining itself.
            M3Dialog.HeadlineRowKept -> return null
            // A bare title(): nothing around it to worry about.
            else -> headline.visibility = View.GONE
        }
        // Only the headline moves either way. A header's supporting line explains the screen and
        // belongs on it.
        return text
    }

    private fun findHeadline(view: View): TextView? {
        if (view.tag === M3Dialog.Headline) return view as? TextView
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findHeadline(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // ---- Coming and going ----

    /**
     * How far it travels: the shared-axis transition's 30dp. A fixed distance rather than a
     * fraction of the page's width, because the width is not known yet: a page animates in the
     * moment it is added, and waiting for a layout pass to measure it means either a posted
     * runnable that can land a frame late or a first frame with nothing on it.
     */
    private const val SLIDE_DP = 30f

    /**
     * Where the bottom bar's top edge is, measured up from the bottom of the window.
     *
     * The bar is 64dp tall and stands 12dp off the navigation inset (GlassBarScaffold), so this is
     * those two added up -- not the 96dp the tabs reserve, which carries extra air a tab wants
     * under its last row and a page's button row does not. Guessing high here is exactly what put
     * a band of nothing between the buttons and the bar.
     */
    private const val BAR_TOP_DP = 76

    /**
     * Extra room between the scrolled content and a pinned action row, on top of whatever
     * [M3Dialog.panel]'s own bottom padding (24dp) already contributes on the content side.
     * Without this the two clearances -- the row's own height and the panel's existing gutter --
     * add up to the same total a *stacked* row's 20dp top padding used to give, which pinning was
     * supposed to improve on and would otherwise just relocate.
     */
    private const val ROW_GAP_DP = 16

    /**
     * Going back: what the closing page uncovers slides in from the left on the same Motion
     * intensity spring a page opens with, so Q-bouncy overshoots and settles both ways.
     */
    private fun reveal(host: ViewGroup, target: View?) {
        val from = -SLIDE_DP * host.resources.displayMetrics.density
        if (target != null) {
            MotionPrefs.slideIn(target, from)
            return
        }
        val pages = stacks.values.flatten().map { it.view }
        for (i in 0 until host.childCount) {
            val child = host.getChildAt(i)
            if (pages.none { it === child } && child.visibility == View.VISIBLE) {
                MotionPrefs.slideIn(child, from)
            }
        }
    }

    private fun enter(page: View) =
        MotionPrefs.slideIn(page, SLIDE_DP * page.resources.displayMetrics.density)

    private fun exit(page: View, then: () -> Unit) =
        MotionPrefs.slideOut(page, SLIDE_DP * page.resources.displayMetrics.density, then)

    /** True when [scroller]'s content fits below the expanded title without scrolling. */
    private fun contentFits(column: View, appBar: View, pageContent: View, scroller: View?): Boolean {
        if (scroller == null || column.height == 0) return false
        val content = contentHeight(scroller) ?: return false
        var top = 0
        var view: View = scroller
        while (view !== pageContent) {
            top += view.top
            view = view.parent as? View ?: return false
        }
        return appBar.height + top + content <= column.height - column.paddingBottom
    }

    private fun contentHeight(scroller: View): Int? = when (scroller) {
        // The child's own height is no use: fillViewport stretches it to the viewport.
        is NestedScrollView, is ScrollView -> (scroller as ViewGroup).getChildAt(0)
            ?.let { scroller.paddingTop + it.top + usedHeight(it) + scroller.paddingBottom }
        is android.widget.AbsListView -> scroller.takeIf {
            it.childCount > 0 && it.firstVisiblePosition == 0 &&
                it.lastVisiblePosition == (it.adapter?.count ?: 0) - 1
        }?.let { it.getChildAt(it.childCount - 1).bottom + it.paddingBottom }
        else -> null
    }

    /** How far [view]'s visible children reach, plus its bottom padding. */
    private fun usedHeight(view: View): Int {
        if (view !is ViewGroup || view.childCount == 0) return view.height
        var bottom = view.paddingTop
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child.visibility == View.GONE) continue
            val margin = (child.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            bottom = maxOf(bottom, child.bottom + margin)
        }
        return bottom + view.paddingBottom
    }

    /** The page title's scroll behaviour, switched off while the content fits. */
    private class FitAwareAppBarBehavior(private val fits: () -> Boolean) : AppBarLayout.Behavior() {
        init {
            setDragCallback(object : DragCallback() {
                override fun canDrag(appBarLayout: AppBarLayout) = !fits()
            })
        }

        override fun onStartNestedScroll(
            parent: CoordinatorLayout, child: AppBarLayout, directTargetChild: View,
            target: View, nestedScrollAxes: Int, type: Int
        ): Boolean {
            if (fits()) {
                if (topAndBottomOffset != 0) child.setExpanded(true, true)
                return false
            }
            return super.onStartNestedScroll(parent, child, directTargetChild, target, nestedScrollAxes, type)
        }
    }
}

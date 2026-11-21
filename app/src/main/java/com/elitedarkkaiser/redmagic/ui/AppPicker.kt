package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.Locale

internal data class AppEntry(
    val packageName: String,
    val label: String,
    val game: Boolean = false,
    val system: Boolean = false
)

internal enum class AppFilter(val label: String) {
    GAMES("Games"), ALL("All"), SELECTED("Selected"), DOWNLOADED("Downloaded"), SYSTEM("System")
}

/**
 * The app's one app picker: search, filter chips and a grouped list of installed apps.
 *
 * Single mode closes on the first tap and hands back the app. Multi mode toggles apps in place and
 * reports the whole selection after every change.
 */
internal object AppPicker {
    sealed interface Mode {
        class Single(val current: String?, val onPicked: (AppEntry) -> Unit) : Mode
        class Multi(val initial: Set<String>, val onChanged: (Set<String>) -> Unit) : Mode
    }

    internal fun filter(entries: List<AppEntry>, filter: AppFilter, query: String,
        selected: Set<String> = emptySet()): List<AppEntry> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return entries.filter {
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.GAMES -> it.game
                AppFilter.SELECTED -> it.packageName in selected
                AppFilter.DOWNLOADED -> !it.system
                AppFilter.SYSTEM -> it.system
            } && (needle.isEmpty() || it.label.lowercase(Locale.ROOT).contains(needle) ||
                it.packageName.lowercase(Locale.ROOT).contains(needle))
        }
    }

    /** Every app with a launcher entry, except this one. */
    @Suppress("DEPRECATION")
    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).asSequence()
            .filter { it.packageName != context.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                AppEntry(it.packageName,
                    runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(it.packageName),
                    game = it.category == ApplicationInfo.CATEGORY_GAME || it.flags and ApplicationInfo.FLAG_IS_GAME != 0,
                    system = it.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }.toList()
    }

    fun show(
        activity: Activity,
        title: String,
        supporting: String,
        mode: Mode,
        filters: List<AppFilter> = listOf(AppFilter.ALL, AppFilter.DOWNLOADED, AppFilter.SYSTEM),
        /** Labels the trailing tag of a selected app in single mode, e.g. "Added". */
        selectedTag: String = "Selected",
        /** Packages to mark as selected in single mode, when more than [Mode.Single.current]. */
        marked: Set<String> = emptySet(),
        load: (Context) -> List<AppEntry> = ::launchable
    ) {
        val m3 = M3(activity)
        lateinit var panel: Panel
        val selected = when (mode) {
            is Mode.Multi -> mode.initial.toMutableSet()
            is Mode.Single -> (marked + listOfNotNull(mode.current)).toMutableSet()
        }
        var entries = emptyList<AppEntry>()
        var shown = emptyList<AppEntry>()
        var selection = filters.first()
        var query = ""
        var loading = true

        val icons = LruCache<String, Drawable>(64)
        fun icon(pkg: String): Drawable? = icons.get(pkg) ?: runCatching {
            activity.packageManager.getApplicationIcon(pkg)
        }.getOrElse { activity.getDrawable(android.R.drawable.sym_def_app_icon) }?.also { icons.put(pkg, it) }

        fun text(value: String, style: M3.TypeStyle, color: Int) = TextView(activity).apply {
            text = value; m3.styleText(this, style, color)
        }

        val count = text("Loading apps…", M3.Type.labelLarge, m3.onSurfaceVariant).apply {
            setPadding(m3.dp(4), m3.dp(16), m3.dp(4), m3.dp(8))
        }
        val emptyTitle = text("Loading apps", M3.Type.titleMedium, m3.onSurface).apply { gravity = Gravity.CENTER }
        val emptyBody = text("", M3.Type.bodyMedium, m3.onSurfaceVariant).apply {
            gravity = Gravity.CENTER; setPadding(0, m3.dp(8), 0, 0)
        }
        val empty = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(m3.dp(24), m3.dp(24), m3.dp(24), m3.dp(24))
            addView(ShapeChip(activity, Icons.APP_LIST, m3.secondaryContainer, m3.onSecondaryContainer, 28f),
                LinearLayout.LayoutParams(m3.dp(72), m3.dp(72)).apply { bottomMargin = m3.dp(16) })
            addView(emptyTitle); addView(emptyBody)
        }

        val large = m3.dpF(M3.Metrics.groupCorner.toFloat())
        val small = m3.dpF(4f)
        fun rowBackground(position: Int, on: Boolean) = GradientDrawable().apply {
            val top = if (position == 0) large else small
            val bottom = if (position == shown.lastIndex) large else small
            cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            setColor(if (on) m3.secondaryContainer else m3.rowSurface)
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? LinearLayout ?: LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = m3.dp(72)
                    setPadding(m3.dp(16), m3.dp(12), m3.dp(16), m3.dp(12))
                    addView(ImageView(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                        LinearLayout.LayoutParams(m3.dp(44), m3.dp(44)).apply { marginEnd = m3.dp(16) })
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text("", M3.Type.bodyLarge, m3.onSurface).apply {
                            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                        })
                        addView(text("", M3.Type.bodySmall, m3.onSurfaceVariant).apply {
                            maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE; setPadding(0, m3.dp(2), 0, 0)
                        })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(activity).apply {
                        gravity = Gravity.CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(m3.dp(32), m3.dp(32)).apply { marginStart = m3.dp(12) })
                }
                val entry = shown[position]
                val on = entry.packageName in selected
                row.background = rowBackground(position, on)
                (row.getChildAt(0) as ImageView).setImageDrawable(icon(entry.packageName))
                (row.getChildAt(1) as LinearLayout).apply {
                    (getChildAt(0) as TextView).apply {
                        text = entry.label
                        setTextColor(if (on) m3.onSecondaryContainer else m3.onSurface)
                    }
                    (getChildAt(1) as TextView).apply {
                        text = if (on && mode is Mode.Single) "$selectedTag · ${entry.packageName}" else entry.packageName
                        setTextColor(if (on) m3.onSecondaryContainer else m3.onSurfaceVariant)
                    }
                }
                (row.getChildAt(2) as TextView).apply {
                    // Multi mode: an empty ring or a filled check. Single mode: a check on the
                    // current choice and nothing elsewhere.
                    val showMark = on || mode is Mode.Multi
                    visibility = if (showMark) View.VISIBLE else View.INVISIBLE
                    text = if (on) Icons.CHECK else ""
                    m3.styleGlyph(this, 14f, m3.onPrimary)
                    background = if (on) m3.surfaceShape(m3.primary, M3.Shape.full)
                        else m3.surfaceShape(Color.TRANSPARENT, M3.Shape.full, m3.outline)
                }
                row.contentDescription = entry.label + when {
                    mode is Mode.Multi -> if (on) ", selected" else ", not selected"
                    on -> ", $selectedTag"
                    else -> ""
                }
                return row
            }
        }

        lateinit var render: () -> Unit
        val filterGroup = ChoiceGroup(activity, filters.map { Choice(it.label, it) }, filters.first()) {
            selection = it
            render()
        }
        val list = ListView(activity).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            dividerHeight = m3.dp(2)
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            this.adapter = adapter
            emptyView = empty
            M3Dialog.scrollsUnderBar(this)
            setOnItemClickListener { _, _, position, _ ->
                val entry = shown.getOrNull(position) ?: return@setOnItemClickListener
                when (mode) {
                    is Mode.Single -> { panel.dismiss(); mode.onPicked(entry) }
                    is Mode.Multi -> {
                        if (!selected.remove(entry.packageName)) selected.add(entry.packageName)
                        mode.onChanged(selected.toSet())
                        render()
                    }
                }
            }
        }

        render = {
            shown = filter(entries, selection, query, selected)
            adapter.notifyDataSetChanged()
            val noun = if (selection == AppFilter.GAMES) "games" else "apps"
            count.text = when {
                loading -> "Loading apps…"
                mode is Mode.Multi -> "${shown.size} $noun · ${selected.size} selected"
                else -> "${shown.size} $noun"
            }
            emptyTitle.text = when {
                loading -> "Loading apps"
                query.isNotBlank() -> "No matches"
                selection == AppFilter.SELECTED -> "Nothing selected yet"
                selection == AppFilter.GAMES -> "No games found"
                else -> "No apps here"
            }
            emptyBody.text = when {
                loading -> ""
                query.isNotBlank() -> "Try another name or package."
                selection == AppFilter.ALL -> ""
                else -> "Switch to All to see every app."
            }
        }
        val search = M3Dialog.searchField(activity, "Search apps").apply {
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(value: Editable?) { query = value?.toString().orEmpty(); render() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            })
        }

        val content = M3Dialog.panel(activity).apply {
            addView(M3Dialog.header(activity, title, supporting, Icons.APP_LIST))
            addView(M3Dialog.vGap(activity, 16))
            addView(search)
            if (filters.size > 1) {
                addView(filterGroup.view, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = m3.dp(12) })
            }
            addView(count)
            addView(FrameLayout(activity).apply {
                addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        render()
        panel = M3Dialog.show(activity, content)

        val context = activity.applicationContext
        Thread({
            val loaded = runCatching { load(context) }.getOrDefault(emptyList())
            activity.runOnUiThread {
                if (activity.isDestroyed || !panel.isShowing) return@runOnUiThread
                entries = loaded
                loading = false
                // Start on Games only when there are some; otherwise the list opens empty.
                if (selection == AppFilter.GAMES && loaded.none { it.game } && AppFilter.ALL in filters) {
                    selection = AppFilter.ALL
                    filterGroup.value = AppFilter.ALL
                }
                render()
            }
        }, "app-picker").start()
    }
}

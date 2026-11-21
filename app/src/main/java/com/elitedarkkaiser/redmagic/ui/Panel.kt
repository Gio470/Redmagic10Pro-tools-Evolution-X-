package com.elitedarkkaiser.redmagic.ui

/** A shared close/cancel handle for a full [PageHost] page or a compact [CardDrawer]. */
class Panel internal constructor(
    /** Whether backing out of it closes it. False for the ones that are waiting for an answer. */
    internal val cancelable: Boolean,
    private val remove: (Panel) -> Unit
) {
    private var onDismiss: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var gone = false

    fun setOnDismissListener(listener: () -> Unit) { onDismiss = listener }

    fun setOnCancelListener(listener: () -> Unit) { onCancel = listener }

    /** Closed deliberately: a Done or Close button, or the work finishing. */
    fun dismiss() = close(cancelled = false)

    /** Backed out of, which is what tapping outside a sheet used to be. */
    internal fun cancel() = close(cancelled = true)

    val isShowing: Boolean get() = !gone

    private fun close(cancelled: Boolean) {
        if (gone) return
        gone = true
        if (cancelled) onCancel?.invoke()
        remove(this)
        onDismiss?.invoke()
    }
}


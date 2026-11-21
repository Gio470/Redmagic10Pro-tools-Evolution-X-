package com.elitedarkkaiser.redmagic.parts

/** Loaded only by the root helper, never by the app's UI process. */
internal object PartsNative {
    external fun enterMountNamespace(): Int
    external fun bindProfile(source: String): Int
    external fun unmountProfile(): Int
    external fun openEvents(): Int
    external fun nextEvent(fd: Int): String?
    external fun closeEvents(fd: Int)
}

package com.elitedarkkaiser.redmagic.ui

import android.app.Activity
import android.widget.LinearLayout

data class SoftwareTabDeps(
    val scrollTabContainer: () -> LinearLayout,
    /** The host, for the pages and dialogs the Software tab opens. */
    val activity: () -> Activity
)


package com.elitedarkkaiser.redmagic.state

data class LedState(
    val enabled: Boolean = false,
    val effect: String = "steady",
    val color: Int = 5
)


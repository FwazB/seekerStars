package com.portalritual.engine

enum class RitualPhase {
    IDLE,
    MANIFESTING,
    ALIGN_1,
    ALIGN_2,
    ALIGN_3,
    TRACE_RUNE,
    STABILIZED,
    RESULTS,
    COLLAPSED;

    val isAlignment: Boolean
        get() = this == ALIGN_1 || this == ALIGN_2 || this == ALIGN_3

    val isTerminal: Boolean
        get() = this == RESULTS || this == COLLAPSED

    val activeRingIndex: Int
        get() = when (this) {
            ALIGN_1 -> 0
            ALIGN_2 -> 1
            ALIGN_3 -> 2
            else -> -1
        }

    fun nextAfterLock(): RitualPhase = when (this) {
        ALIGN_1 -> ALIGN_2
        ALIGN_2 -> ALIGN_3
        ALIGN_3 -> TRACE_RUNE
        else -> this
    }
}

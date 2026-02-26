package com.portalritual.engine

enum class ConstellationPhase {
    IDLE,
    CONSTELLATION_ACTIVE,
    CONSTELLATION_COMPLETE,
    TRACE_RUNE,
    RESULTS,
    COLLAPSED;

    val isTerminal: Boolean get() = this == RESULTS || this == COLLAPSED
}

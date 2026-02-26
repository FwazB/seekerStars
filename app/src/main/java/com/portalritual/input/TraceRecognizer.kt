package com.portalritual.input

/**
 * $1 recognizer interface for rune tracing.
 * Contract for deimos (Input/Rune worker).
 */
interface TraceRecognizer {
    fun recognize(points: List<Pair<Float, Float>>): TraceResult
}

class StubTraceRecognizer : TraceRecognizer {
    override fun recognize(points: List<Pair<Float, Float>>): TraceResult {
        return TraceResult(templateName = null, score = 0f, accepted = false)
    }
}

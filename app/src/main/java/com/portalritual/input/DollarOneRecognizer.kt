package com.portalritual.input

import kotlin.math.*

/**
 * $1 Unistroke Recognizer — recognizes drawn gestures against stored rune templates.
 *
 * Pipeline: resample → rotate to indicative angle → scale to unit box → translate to origin → compare.
 * Reference: Wobbrock, Wilson & Li, "Gestures without Libraries" (UIST 2007).
 */
class DollarOneRecognizer(
    private val threshold: Float = 0.7f,
    private val numPoints: Int = 64
) : TraceRecognizer {

    private val templates: List<Template> = RuneTemplates.all(numPoints)
    private val halfDiagonal: Float = sqrt(2f) / 2f

    data class Template(val name: String, val points: List<Pair<Float, Float>>)

    override fun recognize(points: List<Pair<Float, Float>>): TraceResult {
        if (points.size < 2) {
            return TraceResult(templateName = null, score = 0f, accepted = false)
        }

        val len = pathLength(points)
        if (len < 1e-6f) {
            return TraceResult(templateName = null, score = 0f, accepted = false)
        }

        // $1 pipeline
        val resampled = resample(points, numPoints)
        val rotated = rotateToZero(resampled)
        val scaled = scaleToSquare(rotated, 1f)
        val translated = translateToOrigin(scaled)

        var bestScore = Float.NEGATIVE_INFINITY
        var bestName: String? = null

        for (tmpl in templates) {
            val d = distanceAtBestAngle(translated, tmpl.points, -PI.toFloat() / 4f, PI.toFloat() / 4f, (2f * PI / 180f).toFloat())
            val score = 1f - d / halfDiagonal
            if (score > bestScore) {
                bestScore = score
                bestName = tmpl.name
            }
        }

        return TraceResult(
            templateName = if (bestScore >= threshold) bestName else null,
            score = bestScore.coerceIn(0f, 1f),
            accepted = bestScore >= threshold
        )
    }

    // --- Resample to N equidistant points ---

    internal fun resample(points: List<Pair<Float, Float>>, n: Int): List<Pair<Float, Float>> {
        val totalLen = pathLength(points)
        val interval = totalLen / (n - 1)
        val result = mutableListOf(points[0])
        var carry = 0f
        var i = 1

        val pts = points.toMutableList()
        while (i < pts.size && result.size < n) {
            val d = dist(pts[i - 1], pts[i])
            if (carry + d >= interval) {
                val ratio = (interval - carry) / d
                val nx = pts[i - 1].first + ratio * (pts[i].first - pts[i - 1].first)
                val ny = pts[i - 1].second + ratio * (pts[i].second - pts[i - 1].second)
                val newPt = Pair(nx, ny)
                result.add(newPt)
                pts.add(i, newPt)
                carry = 0f
            } else {
                carry += d
            }
            i++
        }

        // Pad if rounding leaves us short
        while (result.size < n) result.add(pts.last())
        return result.take(n)
    }

    // --- Rotate so indicative angle (centroid → first point) is at 0° ---

    internal fun rotateToZero(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val c = centroid(points)
        val theta = atan2(points[0].second - c.second, points[0].first - c.first)
        return rotateBy(points, -theta)
    }

    private fun rotateBy(points: List<Pair<Float, Float>>, angle: Float): List<Pair<Float, Float>> {
        val c = centroid(points)
        val cosA = cos(angle)
        val sinA = sin(angle)
        return points.map { p ->
            val dx = p.first - c.first
            val dy = p.second - c.second
            Pair(dx * cosA - dy * sinA + c.first, dx * sinA + dy * cosA + c.second)
        }
    }

    // --- Scale to reference square ---

    internal fun scaleToSquare(points: List<Pair<Float, Float>>, size: Float): List<Pair<Float, Float>> {
        val (minX, maxX, minY, maxY) = boundingBox(points)
        val w = maxX - minX
        val h = maxY - minY
        if (w < 1e-6f || h < 1e-6f) return points
        return points.map { p ->
            Pair(p.first * (size / w), p.second * (size / h))
        }
    }

    // --- Translate centroid to origin ---

    internal fun translateToOrigin(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val c = centroid(points)
        return points.map { p -> Pair(p.first - c.first, p.second - c.second) }
    }

    // --- Golden Section Search for best angle ---

    private fun distanceAtBestAngle(
        candidate: List<Pair<Float, Float>>,
        template: List<Pair<Float, Float>>,
        fromAngle: Float,
        toAngle: Float,
        threshold: Float
    ): Float {
        val phi = 0.5f * (-1f + sqrt(5f)) // golden ratio
        var a = fromAngle
        var b = toAngle
        var x1 = phi * a + (1f - phi) * b
        var f1 = distanceAtAngle(candidate, template, x1)
        var x2 = (1f - phi) * a + phi * b
        var f2 = distanceAtAngle(candidate, template, x2)

        while (abs(b - a) > threshold) {
            if (f1 < f2) {
                b = x2
                x2 = x1
                f2 = f1
                x1 = phi * a + (1f - phi) * b
                f1 = distanceAtAngle(candidate, template, x1)
            } else {
                a = x1
                x1 = x2
                f1 = f2
                x2 = (1f - phi) * a + phi * b
                f2 = distanceAtAngle(candidate, template, x2)
            }
        }
        return minOf(f1, f2)
    }

    private fun distanceAtAngle(
        candidate: List<Pair<Float, Float>>,
        template: List<Pair<Float, Float>>,
        angle: Float
    ): Float {
        val rotated = rotateBy(candidate, angle)
        return pathDistance(rotated, template)
    }

    // --- Utility functions ---

    internal fun centroid(points: List<Pair<Float, Float>>): Pair<Float, Float> {
        var sx = 0f; var sy = 0f
        for (p in points) { sx += p.first; sy += p.second }
        return Pair(sx / points.size, sy / points.size)
    }

    private fun boundingBox(points: List<Pair<Float, Float>>): FloatArray {
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.first < minX) minX = p.first
            if (p.first > maxX) maxX = p.first
            if (p.second < minY) minY = p.second
            if (p.second > maxY) maxY = p.second
        }
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    internal fun pathDistance(a: List<Pair<Float, Float>>, b: List<Pair<Float, Float>>): Float {
        val n = minOf(a.size, b.size)
        var sum = 0f
        for (i in 0 until n) sum += dist(a[i], b[i])
        return sum / n
    }

    internal fun pathLength(points: List<Pair<Float, Float>>): Float {
        var len = 0f
        for (i in 1 until points.size) len += dist(points[i - 1], points[i])
        return len
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }
}

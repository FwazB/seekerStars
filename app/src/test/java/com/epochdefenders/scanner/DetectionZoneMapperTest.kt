package com.epochdefenders.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionZoneMapperTest {

    private val mapper = DetectionZoneMapper(gridCols = 12, gridRows = 9)

    @Test
    fun `single detection maps to correct cells`() {
        // Box at top-left quarter: x=0, y=0, w=0.25, h=0.33 → cols 0-2, rows 0-2
        val box = NormalizedBox(x = 0f, y = 0f, width = 0.25f, height = 0.33f)
        val cells = mapper.mapDetections(listOf(box))
        // cols: 0*12=0 to 0.25*12=3 → 0..3
        // rows: 0*9=0 to 0.33*9=2 → 0..2
        assertTrue(cells.contains(GridCell(0, 0)))
        assertTrue(cells.contains(GridCell(3, 2)))
    }

    @Test
    fun `detection below score threshold is ignored`() {
        val box = NormalizedBox(x = 0.5f, y = 0.5f, width = 0.1f, height = 0.1f, score = 0.3f)
        val cells = mapper.mapDetections(listOf(box), minScore = 0.5f)
        assertTrue(cells.isEmpty())
    }

    @Test
    fun `detection at score threshold is included`() {
        val box = NormalizedBox(x = 0.5f, y = 0.5f, width = 0.1f, height = 0.1f, score = 0.5f)
        val cells = mapper.mapDetections(listOf(box), minScore = 0.5f)
        assertTrue(cells.isNotEmpty())
    }

    @Test
    fun `full-frame detection covers all cells`() {
        val box = NormalizedBox(x = 0f, y = 0f, width = 1f, height = 1f)
        val cells = mapper.mapDetections(listOf(box))
        assertEquals(12 * 9, cells.size)
    }

    @Test
    fun `empty detections returns empty set`() {
        val cells = mapper.mapDetections(emptyList())
        assertTrue(cells.isEmpty())
    }

    @Test
    fun `overlapping detections deduplicate cells`() {
        val box1 = NormalizedBox(x = 0f, y = 0f, width = 0.5f, height = 0.5f)
        val box2 = NormalizedBox(x = 0.25f, y = 0.25f, width = 0.5f, height = 0.5f)
        val cells = mapper.mapDetections(listOf(box1, box2))
        // Overlapping cells should appear only once
        val uniqueCount = cells.size
        assertTrue(uniqueCount > 0)
        assertEquals(uniqueCount, cells.toSet().size)
    }

    @Test
    fun `out-of-bounds coords are clamped`() {
        val box = NormalizedBox(x = -0.1f, y = -0.1f, width = 1.5f, height = 1.5f)
        val cells = mapper.mapDetections(listOf(box))
        // Should clamp to full grid, not crash
        assertEquals(12 * 9, cells.size)
    }

    @Test
    fun `small detection maps to at least one cell`() {
        // Tiny box in center
        val box = NormalizedBox(x = 0.5f, y = 0.5f, width = 0.01f, height = 0.01f)
        val cells = mapper.mapDetections(listOf(box))
        assertTrue(cells.isNotEmpty())
    }
}

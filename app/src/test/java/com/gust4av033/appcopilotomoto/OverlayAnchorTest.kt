package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAnchorTest {
    @Test fun anchorStaysAtTheChosenEdgeAfterRotationAndPanelExpansion() {
        val anchor = OverlayAnchor(right = true, verticalFraction = 0.6f)
        val portrait = anchor.place(360, 740, 56, 56, 8)
        val landscape = anchor.place(740, 360, 260, 320, 8)
        assertEquals(296, portrait.x)
        assertEquals(472, landscape.x)
        assertTrue(landscape.y >= 8 && landscape.y + 320 <= 352)
        assertEquals(8, anchor.copy(right = false).place(740, 360, 56, 56, 8).x)
    }

    @Test fun savedPositionCannotPutControlsOutsideTheScreen() {
        for (fraction in listOf(-4f, 5f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val point = OverlayAnchor(verticalFraction = fraction).place(320, 640, 260, 320, 8)
            assertTrue(point.y in 8..312)
            assertTrue(point.x in 8..52)
        }
    }

    @Test fun tooSmallViewportStillProducesNonNegativeCoordinates() {
        assertEquals(OverlayPoint(0, 0), OverlayAnchor().place(40, 40, 56, 56, 8))
    }
}

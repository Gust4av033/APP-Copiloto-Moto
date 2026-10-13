package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class OverlayPanelBoundsTest {
    @Test fun normalPhoneKeepsCompactWidthAndHorizontalActions() {
        val panel = OverlayPanelBounds.fit(1080, 2200, 3f, 1f)
        assertEquals(244 * 3, panel.widthPx)
        assertFalse(panel.stackedActions)
    }

    @Test fun largeTextUsesStackedActionsWithoutCoveringTheWholePhone() {
        val panel = OverlayPanelBounds.fit(1080, 2200, 3f, 2f)
        assertTrue(panel.stackedActions)
        assertTrue(panel.widthPx < 1080 - 48)
    }

    @Test fun narrowWindowFitsWithMarginsAndStacksTheActions() {
        val panel = OverlayPanelBounds.fit(200, 400, 1f, 1f)
        assertEquals(184, panel.widthPx)
        assertTrue(panel.stackedActions)
    }

    @Test fun landscapeHeightRemainsInsideUsableScreen() {
        val panel = OverlayPanelBounds.fit(780, 280, 1f, 1f)
        assertEquals(264, panel.maxHeightPx)
        assertEquals(244, panel.widthPx)
    }

    @Test fun densityChangesPreservePhysicalDesignSize() {
        for (density in listOf(1f, 2f, 3f)) {
            val panel = OverlayPanelBounds.fit((360 * density).toInt(), (740 * density).toInt(), density, 1f)
            assertEquals(244f, panel.widthPx / density, 0.1f)
            assertTrue(panel.maxHeightPx < 740 * density)
        }
    }
}

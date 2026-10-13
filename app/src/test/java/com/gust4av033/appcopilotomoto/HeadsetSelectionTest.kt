package com.gust4av033.appcopilotomoto

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState

class HeadsetSelectionTest {
    private val y12 = HeadsetIdentity(11, 7, "y12-address", "Y12 Pro")
    private val skullcandy = HeadsetIdentity(22, 7, "skull-address", "Skullcandy")

    @After fun reset() { HeadsetSelection.select(null) }

    @Test fun explicitChoiceWorksWithTwoHeadsets() {
        HeadsetSelection.select(skullcandy)
        assertEquals(skullcandy, HeadsetSelection.resolve(listOf(y12, skullcandy)))
    }

    @Test fun disconnectDoesNotSelectTheOtherHeadset() {
        HeadsetSelection.select(y12)
        assertNull(HeadsetSelection.resolve(listOf(skullcandy)))
        assertEquals(y12, HeadsetSelection.preferred)
    }

    @Test fun recycledIdCannotChangeHardwareOrProfile() {
        HeadsetSelection.select(y12)
        assertNull(HeadsetSelection.resolve(listOf(skullcandy.copy(id = y12.id))))
        assertNull(HeadsetSelection.resolve(listOf(y12.copy(type = 26))))
        assertNull(HeadsetSelection.resolve(listOf(y12.copy(id = 33))))
    }

    @Test fun anonymousDevicesWithRecycledIdsMustBeReselected() {
        HeadsetSelection.select(y12.copy(address = ""))
        assertNull(HeadsetSelection.resolve(listOf(skullcandy.copy(id = y12.id, address = ""))))
    }

    @Test fun automaticModeRemovesManualOverride() {
        HeadsetSelection.select(y12)
        HeadsetSelection.select(null)
        assertNull(HeadsetSelection.preferred)
        assertNull(HeadsetSelection.resolve(listOf(y12)))
    }

    @Test fun audioRestorationKeepsNewCapturesAndDeviceChangesLocked() {
        val restoring = CopilotUiState(restoringAudio = true)
        assertTrue(restoring.busy)
        assertFalse(restoring.canListen)
        assertFalse(restoring.canToggleWake)
        assertFalse(restoring.copy(restoringAudio = false).busy)
    }
}

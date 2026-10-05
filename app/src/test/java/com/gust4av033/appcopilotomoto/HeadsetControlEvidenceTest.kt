package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class HeadsetControlEvidenceTest {
    @Test fun sameModelNameDoesNotMergeTwoDifferentHeadsets() {
        val first = HeadsetIdentity(1, 7, "AA:BB:CC:00:00:01", "Y12 Pro")
        val second = first.copy(id = 2, address = "AA:BB:CC:00:00:02")
        assertNotEquals(HeadsetControlEvidence.persistentId(first), HeadsetControlEvidence.persistentId(second))
    }

    @Test fun reconnectingOrRenamingTheSameDeviceRetainsItsProfile() {
        val original = HeadsetIdentity(1, 7, "AA:BB:CC:00:00:01", "Y12 Pro")
        val reconnected = original.copy(id = 300, type = 26, name = "Mi casco", address = "aa:bb:cc:00:00:01")
        assertEquals(HeadsetControlEvidence.persistentId(original), HeadsetControlEvidence.persistentId(reconnected))
    }

    @Test fun missingStableIdentityNeverPersistsByModelName() {
        assertNull(HeadsetControlEvidence.persistentId(HeadsetIdentity(1, 7, "", "Y12 Pro")))
        assertNull(HeadsetControlEvidence.persistentId(HeadsetIdentity(2, 7, "  ", "Skullcandy")))
    }

    @Test fun observedKeysAccumulateWithoutInventingOtherControls() {
        val profile = HeadsetControlEvidence().observeKey(88).observeKey(88).observeKey(87)
        assertEquals(setOf(87, 88), profile.mediaKeys)
        assertFalse(profile.assistantInvocation)
        assertFalse(85 in profile.mediaKeys)
    }

    @Test fun phoneVolumeAndUnknownKeysDoNotProveHeadsetControls() {
        val profile = HeadsetControlEvidence().observeKey(24).observeKey(25).observeKey(0).observeKey(1_000)
        assertTrue(profile.mediaKeys.isEmpty())
        assertFalse(profile.assistantInvocation)
        assertTrue(profile.observeKey(231).assistantInvocation)
    }
}

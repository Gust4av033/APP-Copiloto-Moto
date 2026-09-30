package com.gust4av033.appcopilotomoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeCommandTest {
    @Test fun percentagesAndAbsoluteLevelsAreDistinct() {
        assertEquals(VolumeRequest(40, true), MinimalIntentRouter.requestedVolume("volumen al 40 %"))
        assertEquals(VolumeRequest(65, true), MinimalIntentRouter.requestedVolume("poné el volumen al sesenta y cinco por ciento"))
        assertEquals(VolumeRequest(6, false), MinimalIntentRouter.requestedVolume("volumen a seis"))
        assertEquals(TestIntent.VOLUME_SET, MinimalIntentRouter.resolve("volumen al 40 por ciento"))
    }

    @Test fun outOfRangeOrUnclearRequestsDoNotChangeVolume() {
        assertNull(MinimalIntentRouter.requestedVolume("volumen al 50"))
        assertNull(MinimalIntentRouter.requestedVolume("volumen al 110 por ciento"))
        assertNull(MinimalIntentRouter.requestedVolume("volumen a quince canciones"))
        assertEquals(TestIntent.VOLUME_DOWN, MinimalIntentRouter.resolve("bajar volumen"))
    }
}

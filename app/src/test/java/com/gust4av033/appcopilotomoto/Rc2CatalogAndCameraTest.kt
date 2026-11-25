package com.gust4av033.appcopilotomoto

import com.gust4av033.appcopilotomoto.presentation.components.TripMapCameraMode
import com.gust4av033.appcopilotomoto.presentation.components.tripMapHeadingDegrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Rc2CatalogAndCameraTest {
    @Test fun heroIgnitorIsTheOnlyPreservedLocalCatalogModel() {
        assertEquals(1, MotorcycleCatalog.models.size)
        val model = MotorcycleCatalog.find("hero_ignitor_125")
        assertNotNull(model)
        assertEquals("Hero Ignitor 125", model!!.displayName)
        assertNull(MotorcycleCatalog.find("unknown_model"))
    }

    @Test fun headingRequiresMovingSpeedAndPreciseBearing() {
        val valid = LocationSample(1000, 1000, 14.6, -90.5, null, 3f, null, 90f, 12f, 5f, "gps", false)
        assertEquals(90.0, tripMapHeadingDegrees(valid)!!, 0.001)
        assertNull(tripMapHeadingDegrees(valid.copy(speedMetersPerSec = 1f)))
        assertNull(tripMapHeadingDegrees(valid.copy(bearingAccuracyDegrees = 31f)))
        assertNull(tripMapHeadingDegrees(valid.copy(bearing = null)))
    }

    @Test fun cameraModesRemainDistinct() {
        assertEquals(3, TripMapCameraMode.entries.size)
        assertNotNull(TripMapCameraMode.valueOf("FOLLOW_HEADING"))
        assertNotNull(TripMapCameraMode.valueOf("NORTH_UP"))
        assertNotNull(TripMapCameraMode.valueOf("FREE"))
    }
}

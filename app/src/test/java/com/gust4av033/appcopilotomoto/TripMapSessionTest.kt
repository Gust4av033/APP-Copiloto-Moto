package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripMapSessionTest {
    private fun fix(time: Long, lon: Double = -90.5, speed: Float? = 10f, simulated: Boolean = false) =
        LocationSample(1_800_000_000_000 + time, time, 14.6, lon, null, speed, .3f, null, null, 3f, "gps", simulated)

    @Test fun distanceAndAcceptedSamplesAreExactlyTheExistingRecorders() {
        val dir = Files.createTempDirectory("trip-map-recorder").toFile()
        try {
            val repo = TripRepository(dir); repo.start(1_800_000_000_000)
            val track = TripTrackAccumulator(); track.start("session", 1000)
            for (sample in listOf(fix(1000), fix(2000, -90.4999), fix(2000, -90.4998),
                fix(3000, -90.4997, simulated = true), fix(4000, -90.4997))) {
                val count = repo.currentSampleCount
                repo.append(sample, MovementState.EN_MARCHA)
                if (repo.currentSampleCount > count) track.acceptRecorded(sample, repo.currentDistanceMeters)
            }
            assertEquals(3, repo.currentSampleCount)
            assertEquals(repo.currentDistanceMeters, track.snapshot.distanceMeters!!, 0.0)
            assertEquals(ContextSource.ANDROID_GPS, track.snapshot.lastFix!!.source)
            assertFalse(track.snapshot.lastFix!!.isSimulated)
            assertEquals(4000L, track.snapshot.lastFix!!.capturedAtElapsedRealtimeMs)
            track.finish(10_000)
            assertEquals(repo.finish(1_800_000_010_000)!!.distanceMeters, track.snapshot.distanceMeters!!, 0.0)
        } finally { dir.deleteRecursively() }
    }

    @Test fun gapsStoppedDriftAndAntimeridianNeverProduceImaginaryConnectingLines() {
        val track = TripTrackAccumulator(); track.start("id", 1000)
        track.acceptRecorded(fix(1000), 0.0)
        track.acceptRecorded(fix(2000, -90.4999), 10.0)
        track.acceptRecorded(fix(30_000, -90.4), 10.0)
        track.acceptRecorded(fix(31_000, -90.3999), 20.0)
        assertEquals(listOf(listOf(1000L, 2000L), listOf(30_000L, 31_000L)),
            track.snapshot.segments.map { segment -> segment.map { it.elapsedMs } })
        track.acceptRecorded(fix(32_000, -90.3998, speed = 0f), 20.0)
        track.acceptRecorded(fix(33_000, -90.3997), 20.0)
        track.acceptRecorded(fix(34_000, -90.3996), 30.0)
        assertEquals(3, track.snapshot.segments.size)
        val dateline = TripTrackAccumulator(); dateline.start("date", 0)
        dateline.acceptRecorded(fix(1000, 179.9999), 0.0)
        dateline.acceptRecorded(fix(2000, -179.9999), 20.0)
        assertTrue(dateline.snapshot.segments.isEmpty())
    }

    @Test fun speedExpiresAndUnavailableNeverBecomesStationaryZero() {
        val track = TripTrackAccumulator(); track.start("id", 1000)
        assertNull(track.snapshot.speedKmh(1000))
        assertNull(track.snapshot.distanceMeters)
        track.acceptRecorded(fix(2000), 0.0)
        assertEquals(36f, track.snapshot.speedKmh(3000)!!, .01f)
        assertNull(track.snapshot.speedKmh(1999))
        assertNull(track.snapshot.speedKmh(7000))
        track.acceptRecorded(fix(8000, speed = null), 0.0)
        assertNull(track.snapshot.speedKmh(8000))
        track.acceptRecorded(fix(9000, speed = 0f), 0.0)
        assertEquals(0f, track.snapshot.speedKmh(9000)!!, 0f)
        track.finish(10_000)
        assertNull(track.snapshot.speedKmh(10_000))
        assertEquals(9000L, track.snapshot.durationMs(50_000))
        val closed = track.snapshot
        track.acceptRecorded(fix(11_000), 100.0)
        assertEquals(closed, track.snapshot)
        track.start("new", 20_000)
        assertNull(track.snapshot.lastFix)
        assertNull(track.snapshot.distanceMeters)
    }

    @Test fun simulatedPoorAndOutOfOrderFixesAreRejectedAndMemoryIsBounded() {
        val track = TripTrackAccumulator(); track.start("id", 1000)
        track.acceptRecorded(fix(1000, simulated = true), 0.0)
        track.acceptRecorded(fix(2000).copy(horizontalAccuracyMeters = 100f), 0.0)
        assertNull(track.snapshot.lastFix)
        for (i in 1..12_000) track.acceptRecorded(fix(i * 1000L, -90.5 + i * .00001), i * 1.0)
        assertTrue(track.snapshot.segments.sumOf { it.size } <= TripTrackAccumulator.MAX_POINTS)
        assertTrue(track.snapshot.simplified)
        assertEquals(12_000.0, track.snapshot.distanceMeters!!, 0.0)
        val before = track.snapshot
        track.acceptRecorded(fix(1), 20000.0)
        assertEquals(before, track.snapshot)
        track.clear(); assertNull(track.snapshot.sessionId)
    }
}

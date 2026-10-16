package com.gust4av033.appcopilotomoto

import android.content.pm.ServiceInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripContextTest {
    private fun fix(elapsed: Long, lon: Double, speed: Float = 8f, accuracy: Float = 4f,
        simulated: Boolean = false) = LocationSample(1_700_000_000_000 + elapsed, elapsed,
        13.7, lon, 620.0, speed, .3f, 90f, 2f, accuracy, "gps", simulated)

    @Test fun monotonicFreshnessAndFusionDiscardExpiredValues() {
        val notice = ContextValue("Gira a la derecha", ContextSource.WAZE_NOTIFICATION, 100, 100,
            .6f, 1000)
        val screen = ContextValue("Gira a la izquierda", ContextSource.WAZE_ACCESSIBILITY, 110, 110,
            .9f, 1000)
        assertEquals(Freshness.FRESH, notice.freshness(150))
        assertEquals(Freshness.STALE, notice.freshness(700))
        assertEquals(Freshness.EXPIRED, notice.freshness(1100))
        assertEquals(Freshness.EXPIRED, notice.freshness(99))
        val fused = fuseNavigation(NavigationContext(nextTurnInstruction = notice),
            NavigationContext(nextTurnInstruction = screen), 200)
        assertEquals("Gira a la izquierda", fused.nextTurnInstruction?.value)
        assertNull(fuseNavigation(NavigationContext(nextTurnInstruction = notice), NavigationContext(), 2000).nextTurnInstruction)
        val eta = EtaObservation(EtaSource.WAZE, 12, 100, 100, 1000)
        assertTrue(eta.valid(999)); assertFalse(eta.valid(1100))
    }

    @Test fun replayAlwaysSimulatedAndNeverAcceptedByHistory() {
        val received = mutableListOf<LocationSample>()
        ReplayLocationSource(listOf(fix(100, -89.2))).start(received::add)
        assertTrue(received.single().isSimulated)
        val root = Files.createTempDirectory("trip-replay").toFile()
        try {
            val repository = TripRepository(root)
            repository.start(1_700_000_000_000)
            repository.append(received.single(), MovementState.EN_MARCHA)
            repository.finish(1_700_000_001_000)
            assertTrue(repository.lastTripLines().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun foregroundMaskNeverAddsLocationWithoutAllConditions() {
        for (mask in 0..15) {
            val ready = locationForegroundReady(mask and 1 != 0, mask and 2 != 0,
                mask and 4 != 0, mask and 8 != 0)
            val type = tripForegroundTypes(false, ready)
            assertEquals(mask == 15, type and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        }
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            tripForegroundTypes(false, true) and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    @Test fun movementHysteresisRejectsDriftAndEmitsShadowEvents() {
        val detector = MovementStateDetector(ZoneId.of("UTC"))
        assertTrue(detector.accept(fix(1000, -89.2, 0f, 40f)).isEmpty())
        detector.accept(fix(2000, -89.2, 0f))
        val stop = detector.accept(fix(7000, -89.2, 0f))
        assertEquals(MovementEventKind.STOP_START, stop.single().kind)
        detector.accept(fix(8000, -89.19999, 3f))
        val slow = detector.accept(fix(28000, -89.199, 3f))
        assertEquals(listOf(MovementEventKind.STOP_END, MovementEventKind.SLOW_START), slow.map { it.kind })
        detector.accept(fix(29000, -89.198, 6f))
        val moving = detector.accept(fix(30000, -89.197, 6f))
        assertEquals(MovementEventKind.SLOW_END, moving.single().kind)
    }

    @Test fun privacyTrimRemovesBothEndsAndShortTrips() {
        val short = listOf(fix(0, -89.2), fix(1000, -89.199))
        assertTrue(PrivacyTrim.apply(short).trimmedCompletely)
        val long = (0..10).map { fix(it * 1000L, -89.2 + it * .001) }
        val trimmed = PrivacyTrim.apply(long)
        assertFalse(trimmed.trimmedCompletely)
        assertTrue(trimmed.samples.size in 1..9)
        assertNotEquals(long.first(), trimmed.samples.first())
        assertNotEquals(long.last(), trimmed.samples.last())
    }

    @Test fun repositoryRetainsSummaryThenForgetsEverything() {
        val root = Files.createTempDirectory("trip-history").toFile()
        try {
            val repository = TripRepository(root)
            val start = 1_700_000_000_000L
            repository.start(start)
            repository.append(fix(1000, -89.2), MovementState.EN_MARCHA)
            repository.append(fix(2000, -89.19), MovementState.EN_MARCHA)
            repository.finish(start + 3000)
            val folder = root.resolve("trips")
            assertEquals(1, folder.listFiles { f -> f.extension == "jsonl" }?.size)
            repository.prune(start + TripRepository.RAW_SAMPLE_RETENTION_MS + 1)
            assertEquals(0, folder.listFiles { f -> f.extension == "jsonl" }?.size)
            assertTrue(folder.resolve("trips_index.json").exists())
            repository.forget()
            assertFalse(folder.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun briefVoiceNeverGuessesExpiredRoute() {
        val turn = ContextValue("Gira a la derecha", ContextSource.WAZE_NOTIFICATION,
            100, 100, .8f, 1000)
        val snapshot = TripContextSnapshot(navigation = NavigationContext(nextTurnInstruction = turn))
        assertTrue(BriefTripContextTtsFormatter.route(snapshot, 150).contains("Gira a la derecha"))
        assertTrue(BriefTripContextTtsFormatter.route(snapshot, 1100).contains("No tengo una ruta"))
    }

    @Test fun timeRemainingDistinguishesCurrentLastSeenAndUnknownEta() {
        val current = ContextValue(11, ContextSource.GOOGLE_MAPS_NOTIFICATION, 100, 100, .65f, 15_000)
        val last = LastObservedEta(EtaSource.GOOGLE_MAPS, 12, 100)
        val snapshot = TripContextSnapshot(navigation = NavigationContext(remainingMinutes = current), lastObservedEta = last)
        assertTrue(BriefTripContextTtsFormatter.timeRemaining(snapshot, 200).contains("Faltan 11 minutos según Google Maps"))
        val hidden = snapshot.copy(navigation = NavigationContext())
        val lastAnswer = BriefTripContextTtsFormatter.timeRemaining(hidden, 60_100)
        assertTrue(lastAnswer.contains("último dato de Google Maps, hace 1 minuto"))
        assertTrue(lastAnswer.contains("no tengo una ETA actual"))
        assertFalse(BriefTripContextTtsFormatter.route(hidden, 60_100).contains("12 minutos"))
        assertTrue(BriefTripContextTtsFormatter.timeRemaining(hidden, 120_100).contains("No tengo un tiempo de llegada actual"))
    }

    @Test fun timeRemainingCanGiveOnlyStraightLineDistanceWhenGpsAndTargetAreKnown() {
        val snapshot = TripContextSnapshot(
            location = ContextValue(fix(100, -89.2), ContextSource.ANDROID_GPS, 100, 100, .9f, 10_000),
            target = ContextValue(TargetDestination("Casa", 13.7, -89.19),
                ContextSource.SAVED_DESTINATION_COORDS, 100, 100, 1f, 86_400_000))
        val answer = BriefTripContextTtsFormatter.timeRemaining(snapshot, 200)
        assertTrue(answer.contains("No tengo un tiempo de llegada actual"))
        assertTrue(answer.contains("en línea recta"))
        assertTrue(answer.contains("no es la distancia de la ruta"))
        assertFalse(answer.contains("minutos según"))
    }

    @Test fun navigationParserKeepsProvenanceAndValidEta() {
        val maps = NavigationObservationParser.parse(listOf("Gira a la derecha", "250 m",
            "12 min para llegar", "5 km", "Avenida Los Próceres"),
            ContextSource.GOOGLE_MAPS_NOTIFICATION, 100, 100)
        assertEquals("Google Maps", maps.activeProvider?.value)
        assertEquals(250, maps.distanceToNextTurnMeters?.value)
        assertEquals(12, maps.etaObservation?.remainingMinutes)
        assertEquals(EtaSource.GOOGLE_MAPS, maps.etaObservation?.source)
        assertEquals("Avenida Los Próceres", maps.currentOrTargetStreet?.value)
        assertNull(fuseNavigation(maps, NavigationContext(), 16_000).etaObservation)
    }
}

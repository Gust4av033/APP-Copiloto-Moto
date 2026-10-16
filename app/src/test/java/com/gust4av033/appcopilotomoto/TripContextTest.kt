package com.gust4av033.appcopilotomoto

import android.content.pm.ServiceInfo
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.nio.file.Files
import java.time.ZoneId
import org.json.JSONArray

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

    @Test fun voiceQueriesKeepTurnEtaAndOwnDistanceSeparate() {
        val turn = ContextValue("Gira a la derecha", ContextSource.WAZE_NOTIFICATION,
            100, 100, .8f, 1000)
        val eta = ContextValue(7, ContextSource.GOOGLE_MAPS_NOTIFICATION, 100, 100, .65f, 1000)
        val snapshot = TripContextSnapshot(navigation = NavigationContext(remainingMinutes = eta))
        assertTrue(BriefTripContextTtsFormatter.nextTurn(snapshot, 150).contains("No tengo un próximo giro"))
        assertEquals("Según Google Maps, faltan 7 minutos.", BriefTripContextTtsFormatter.remaining(snapshot, 150))
        assertTrue(BriefTripContextTtsFormatter.remaining(snapshot, 1100).contains("No tengo un tiempo de ruta"))
        val withTurn = snapshot.copy(navigation = NavigationContext(nextTurnInstruction = turn))
        assertTrue(BriefTripContextTtsFormatter.nextTurn(withTurn, 150).contains("Gira a la derecha"))
        assertTrue(BriefTripContextTtsFormatter.remaining(withTurn, 150).contains("No tengo un tiempo de ruta"))

        val location = ContextValue(fix(100, -89.2), ContextSource.ANDROID_GPS, 100, 100, .9f, 10_000)
        val target = ContextValue(TargetDestination("Casa", 13.7, -89.19),
            ContextSource.SAVED_DESTINATION_COORDS, 100, 100, 1f, 86_400_000)
        val own = TripContextSnapshot(location = location, target = target)
        assertTrue(BriefTripContextTtsFormatter.remaining(own, 150).contains("Casa está a"))
        assertTrue(BriefTripContextTtsFormatter.remaining(own, 150).contains("en línea recta"))
        assertFalse(BriefTripContextTtsFormatter.remaining(own, 150).contains("minutos"))
        assertTrue(BriefTripContextTtsFormatter.remaining(own, 11_000).contains("No tengo un tiempo"))
        assertTrue(BriefTripContextTtsFormatter.remaining(TripContextSnapshot(target = target), 150)
            .contains("Falta una posición GPS precisa"))
        assertNull(own.straightLineMeters(86_400_100))
        assertNull(own.targetBearingDegrees(86_400_100))
    }

    @Test fun locationContextRejectsPoorStaleSimulatedAndOutOfOrderFixes() {
        val good = fix(100, -89.2)
        assertTrue(good.usableForTripContext(10_099))
        assertFalse(good.usableForTripContext(10_100))
        assertFalse(good.copy(horizontalAccuracyMeters = 60f).usableForTripContext(200))
        assertFalse(good.copy(isSimulated = true).usableForTripContext(200))
        TripContextCollector.clear()
        try {
            TripContextCollector.recordLocation(good)
            TripContextCollector.recordLocation(fix(200, -89.3, accuracy = 60f))
            TripContextCollector.recordLocation(fix(50, -89.4))
            assertEquals(good, TripContextCollector.latestLocation)
        } finally { TripContextCollector.clear() }
    }

    @Test fun tripStatusOnlySpeaksMovementAndSpeedFromReliableFixes() {
        fun snapshot(sample: LocationSample) = TripContextSnapshot(location = ContextValue(
            sample, ContextSource.ANDROID_GPS, 100, 100, .9f, 10_000))
        val stopped = snapshot(fix(100, -89.2, speed = 0f))
        assertTrue(BriefTripContextTtsFormatter.status(stopped, 150, 0, MovementState.DETENIDO)
            .contains("moto detenida"))
        assertFalse(BriefTripContextTtsFormatter.status(stopped, 150, 0, MovementState.EN_MARCHA)
            .contains("en marcha"))
        val inaccurate = snapshot(fix(100, -89.2, accuracy = 40f))
        assertEquals("No hay contexto reciente del viaje disponible.",
            BriefTripContextTtsFormatter.status(inaccurate, 150, 0, MovementState.EN_MARCHA))
        assertEquals("No hay contexto reciente del viaje disponible.",
            BriefTripContextTtsFormatter.status(stopped, 10_100, 0, MovementState.DETENIDO))
    }

    @Test fun appSessionDurationHasOwnSourceAndExpiresAfterSession() {
        TripNavigationState.endSession()
        try {
            TripNavigationState.beginSession()
            val now = SystemClock.elapsedRealtime()
            val snapshot = TripContextCollector.snapshot(RuntimeEnvironment.getApplication(),
                1_700_000_000_000, now)
            assertEquals(ContextSource.APP_SESSION, snapshot.sessionDurationMs?.source)
            assertTrue(BriefTripContextTtsFormatter.status(snapshot, now, 0)
                .contains("viaje recién iniciado"))
            assertEquals("No hay contexto reciente del viaje disponible.",
                BriefTripContextTtsFormatter.status(snapshot, now + 5_000, 0))
            TripNavigationState.endSession()
            assertNull(TripContextCollector.snapshot(RuntimeEnvironment.getApplication(),
                1_700_000_000_000, now).sessionDurationMs)
        } finally { TripNavigationState.endSession() }
    }

    @Test fun recordedDistanceRejectsDriftAndImpossibleJumps() {
        val start = fix(1000, -89.2, speed = 8f)
        val next = fix(2000, -89.1999, speed = 8f)
        assertTrue(tripDistanceDelta(start, next, MovementState.EN_MARCHA) in 5.0..20.0)
        assertEquals(0.0, tripDistanceDelta(start, next, MovementState.DETENIDO), 0.001)
        assertEquals(0.0, tripDistanceDelta(start.copy(speedMetersPerSec = 0f), next,
            MovementState.EN_MARCHA), 0.001)
        assertEquals(0.0, tripDistanceDelta(start, fix(2000, -89.19), MovementState.EN_MARCHA), 0.001)
        val root = Files.createTempDirectory("trip-drift").toFile()
        try {
            val repository = TripRepository(root)
            repository.start(1_700_000_000_000)
            repository.append(start.copy(speedMetersPerSec = 0f), MovementState.DETENIDO)
            repository.append(next.copy(speedMetersPerSec = 0f), MovementState.DETENIDO)
            repository.finish(1_700_000_003_000)
            val index = JSONArray(root.resolve("trips/trips_index.json").readText())
            assertEquals(0.0, index.getJSONObject(0).getDouble("distanceMeters"), 0.001)
        } finally { root.deleteRecursively() }
    }

    @Test fun timeRemainingDistinguishesCurrentLastSeenAndUnknownEta() {
        val current = ContextValue(11, ContextSource.GOOGLE_MAPS_NOTIFICATION, 100, 100, .65f, 15_000)
        val last = LastObservedEta(EtaSource.GOOGLE_MAPS, 12, 100)
        val snapshot = TripContextSnapshot(navigation = NavigationContext(remainingMinutes = current), lastObservedEta = last)
        assertTrue(BriefTripContextTtsFormatter.remaining(snapshot, 200).contains("Según Google Maps, faltan 11 minutos"))
        val hidden = snapshot.copy(navigation = NavigationContext())
        val lastAnswer = BriefTripContextTtsFormatter.remaining(hidden, 60_100)
        assertTrue(lastAnswer.contains("último dato de Google Maps, hace 1 minuto"))
        assertTrue(lastAnswer.contains("no tengo una ETA actual"))
        assertFalse(BriefTripContextTtsFormatter.nextTurn(hidden, 60_100).contains("12 minutos"))
        assertTrue(BriefTripContextTtsFormatter.remaining(hidden, 120_100).contains("No tengo un tiempo de ruta reciente"))
    }

    @Test fun timeRemainingCanGiveOnlyStraightLineDistanceWhenGpsAndTargetAreKnown() {
        val snapshot = TripContextSnapshot(
            location = ContextValue(fix(100, -89.2), ContextSource.ANDROID_GPS, 100, 100, .9f, 10_000),
            target = ContextValue(TargetDestination("Casa", 13.7, -89.19),
                ContextSource.SAVED_DESTINATION_COORDS, 100, 100, 1f, 86_400_000))
        val answer = BriefTripContextTtsFormatter.remaining(snapshot, 200)
        assertTrue(answer.contains("No tengo tiempo de ruta"))
        assertTrue(answer.contains("en línea recta"))
        assertFalse(answer.contains("faltan 11 minutos"))
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

    @Test fun wazeFieldSampleDoesNotTurnGenericGuidanceIntoAManeuver() {
        val waze = NavigationObservationParser.parse(listOf(
            "No hay señal GPS. Se muestra tu ubicación aproximada.",
            "Sigue la ruta destacada", "PRÓXIMOS GIROS", "REPORTES POR DELANTE",
            "6.1 km", "RN 6 / Carretera a Los Planes", "17 min · 7.1 km"),
            ContextSource.WAZE_ACCESSIBILITY, 100, 100)
        assertEquals("Waze", waze.activeProvider?.value)
        assertNull(waze.nextTurnInstruction)
        assertNull(waze.distanceToNextTurnText)
        assertEquals("RN 6 / Carretera a Los Planes", waze.currentOrTargetStreet?.value)
        assertNull(waze.remainingMinutes)
        assertNull(waze.remainingDistanceText)
        val withGps = NavigationObservationParser.parse(listOf("Sigue la ruta destacada",
            "6.1 km", "RN 6 / Carretera a Los Planes", "17 min · 7.1 km"),
            ContextSource.WAZE_ACCESSIBILITY, 200, 200)
        assertEquals(17, withGps.remainingMinutes?.value)
        assertEquals("7.1 km", withGps.remainingDistanceText?.value)
    }

    @Test fun visibleWazeCannotBorrowAMapsEta() {
        val maps = WazeNotificationSample("maps", "", 1, 1,
            mapOf("text" to "7 min · Llegada: 12:14"), WazeNotificationExtractor.MAPS_PACKAGE, 900)
        val waze = WazeNotificationSample("waze", "", 1, 1,
            mapOf("text" to "Waze en ejecución"), WazeNotificationExtractor.PACKAGE, 800)
        assertEquals(waze, selectNavigationNotification(listOf(maps, waze), 1000, WazeNotificationExtractor.PACKAGE))
        assertEquals(maps, selectNavigationNotification(listOf(maps, waze), 1000, null))
        assertNull(selectNavigationNotification(listOf(maps), 1000, WazeNotificationExtractor.PACKAGE))
        assertNull(selectNavigationNotification(listOf(maps), 61_000, null))
    }
}

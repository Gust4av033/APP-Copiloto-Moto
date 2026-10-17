package com.gust4av033.appcopilotomoto

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Avoid counting stationary GPS drift, poor fixes and impossible jumps as distance ridden. */
internal fun tripDistanceDelta(previous: LocationSample, current: LocationSample,
    state: MovementState): Double {
    if (state != MovementState.EN_MARCHA) return 0.0
    val elapsedSeconds = (current.elapsedRealtimeMs - previous.elapsedRealtimeMs) / 1000.0
    if (elapsedSeconds <= 0 || elapsedSeconds > 15) return 0.0
    val priorSpeed = previous.speedMetersPerSec ?: return 0.0
    val currentSpeed = current.speedMetersPerSec ?: return 0.0
    if (!priorSpeed.isFinite() || !currentSpeed.isFinite() ||
        priorSpeed < MovementStateDetector.STOP_BELOW_MPS ||
        currentSpeed < MovementStateDetector.STOP_BELOW_MPS ||
        priorSpeed > 70f || currentSpeed > 70f) return 0.0
    val priorAccuracy = previous.horizontalAccuracyMeters ?: return 0.0
    val currentAccuracy = current.horizontalAccuracyMeters ?: return 0.0
    if (priorAccuracy > MovementStateDetector.MAX_ACCURACY_METERS ||
        currentAccuracy > MovementStateDetector.MAX_ACCURACY_METERS) return 0.0
    val displacement = distanceMeters(previous.latitude, previous.longitude,
        current.latitude, current.longitude)
    val plausibleMaximum = maxOf(priorSpeed, currentSpeed) * elapsedSeconds * 2 +
        priorAccuracy + currentAccuracy
    return displacement.takeIf { it <= plausibleMaximum } ?: 0.0
}

data class TripSummary(val distanceMeters: Double, val durationMs: Long, val sampleCount: Int)

/** Local append-only history. No simulated sample is accepted. */
class TripRepository(private val filesDir: File) {
    companion object {
        const val RAW_SAMPLE_RETENTION_DAYS = 30L
        const val RAW_SAMPLE_RETENTION_MS = RAW_SAMPLE_RETENTION_DAYS * 24 * 60 * 60 * 1000
    }
    private val directory = File(filesDir, "trips")
    private val index = File(directory, "trips_index.json")
    private var id: String? = null
    private var startedAt = 0L
    private var lastWriteElapsed = 0L
    private var stoppedSinceElapsed: Long? = null
    private val pending = mutableListOf<String>()
    private var lastSample: LocationSample? = null
    private var distance = 0.0
    private var samples = 0
    private var events = 0
    private var stops = 0
    private var slows = 0
    val currentSampleCount get() = samples
    val currentEventCount get() = events

    fun start(epochMs: Long = System.currentTimeMillis()): String {
        check(id == null)
        directory.mkdirs()
        prune(epochMs)
        val newId = "${epochMs}_${UUID.randomUUID()}"
        id = newId; startedAt = epochMs; lastWriteElapsed = 0L; stoppedSinceElapsed = null
        pending.clear(); lastSample = null; distance = 0.0; samples = 0; events = 0; stops = 0; slows = 0
        return newId
    }
    fun append(sample: LocationSample, state: MovementState) {
        if (id == null || !sample.hasTripPositionQuality() ||
            lastSample?.elapsedRealtimeMs?.let { sample.elapsedRealtimeMs <= it } == true) return
        lastSample?.let { distance += tripDistanceDelta(it, sample, state) }
        lastSample = sample; samples++
        pending += JSONObject().put("type", "sample").put("epochMs", sample.epochMs)
            .put("elapsedMs", sample.elapsedRealtimeMs).put("lat", sample.latitude)
            .put("lon", sample.longitude).put("alt", sample.altitude).put("speed", sample.speedMetersPerSec)
            .put("speedAccuracy", sample.speedAccuracyMetersPerSec).put("bearing", sample.bearing)
            .put("bearingAccuracy", sample.bearingAccuracyDegrees).put("accuracy", sample.horizontalAccuracyMeters)
            .put("provider", sample.provider).toString()
        if (state == MovementState.DETENIDO) {
            if (stoppedSinceElapsed == null) stoppedSinceElapsed = sample.elapsedRealtimeMs
        } else stoppedSinceElapsed = null
        val cadence = if (stoppedSinceElapsed?.let { sample.elapsedRealtimeMs - it > 120_000 } == true) 5_000L else 1_000L
        if (lastWriteElapsed == 0L || sample.elapsedRealtimeMs - lastWriteElapsed >= cadence) flush(sample.elapsedRealtimeMs)
    }
    fun append(event: MovementEvent) {
        if (id == null) return
        events++
        if (event.kind == MovementEventKind.STOP_START) stops++
        if (event.kind == MovementEventKind.SLOW_START) slows++
        pending += JSONObject().put("type", "movement").put("kind", event.kind.name)
            .put("epochMs", event.epochMs).put("durationMs", event.durationMs)
            .put("distanceMeters", event.distanceMeters).put("dayOfWeek", event.dayOfWeek.name)
            .put("averageGpsAccuracyMeters", event.averageGpsAccuracyMeters).toString()
    }
    fun append(eta: EtaObservation) {
        if (id == null) return
        pending += JSONObject().put("type", "eta").put("source", eta.source.name)
            .put("remainingMinutes", eta.remainingMinutes).put("epochMs", eta.capturedAtEpochMs)
            .put("elapsedMs", eta.capturedAtElapsedRealtimeMs).put("ttlMs", eta.ttlMs).toString()
    }
    fun finish(epochMs: Long = System.currentTimeMillis()): TripSummary? {
        val currentId = id ?: return null
        if (samples == 0) { abort(); return null }
        flush(lastSample?.elapsedRealtimeMs ?: 0L)
        val summary = TripSummary(distance, (epochMs - startedAt).coerceAtLeast(0), samples)
        val summaries = readIndex()
        summaries.put(JSONObject().put("id", currentId).put("startedAtEpochMs", startedAt)
            .put("endedAtEpochMs", epochMs).put("distanceMeters", distance)
            .put("durationMs", summary.durationMs).put("stops", stops)
            .put("slows", slows).put("samples", samples).put("events", events))
        index.writeText(summaries.toString())
        id = null; pending.clear(); prune(epochMs)
        return summary
    }
    fun abort() {
        id?.let { File(directory, "trip_$it.jsonl").delete() }
        id = null; pending.clear(); lastSample = null
    }
    fun flush(elapsedMs: Long = lastSample?.elapsedRealtimeMs ?: 0L) {
        val currentId = id ?: return
        if (pending.isEmpty()) return
        File(directory, "trip_$currentId.jsonl").appendText(pending.joinToString("\n", postfix = "\n"))
        pending.clear(); lastWriteElapsed = elapsedMs
    }
    fun prune(nowEpochMs: Long = System.currentTimeMillis()) {
        if (!directory.exists()) return
        directory.listFiles { file -> file.name.startsWith("trip_") && file.extension == "jsonl" }
            ?.forEach { file ->
                val epoch = file.name.removePrefix("trip_").substringBefore('_').toLongOrNull()
                if (epoch != null && nowEpochMs - epoch > RAW_SAMPLE_RETENTION_MS) file.delete()
            }
    }
    fun forget() {
        id = null; pending.clear(); lastSample = null
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
    }
    fun lastTripLines(): List<String> {
        val summaries = readIndex()
        if (summaries.length() == 0) return emptyList()
        val currentId = summaries.getJSONObject(summaries.length() - 1).getString("id")
        return File(directory, "trip_$currentId.jsonl").takeIf(File::exists)?.readLines().orEmpty()
    }
    fun exportLast(gpx: Boolean): String {
        val lines = lastTripLines()
        if (lines.isEmpty()) return "Sin viaje guardado para exportar."
        val points = lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("type") == "sample" }.map {
                LocationSample(it.optLong("epochMs"), it.optLong("elapsedMs"), it.optDouble("lat"),
                    it.optDouble("lon"), null, null, null, null, null, null,
                    it.optString("provider"), false)
            }
        val trimmed = PrivacyTrim.apply(points)
        if (gpx) return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"APP Copiloto Moto\"><metadata><name>Viaje real, recortado 300 m en ambos extremos</name></metadata><trk><trkseg>")
            trimmed.samples.forEach { append("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"><time>${Instant.ofEpochMilli(it.epochMs)}</time></trkpt>") }
            append("</trkseg></trk></gpx>")
        }
        val output = JSONObject().put("kind", "real").put("privacyTrimMeters", PRIVACY_TRIM_METERS)
            .put("trimmedCompletely", trimmed.trimmedCompletely)
        val array = JSONArray()
        trimmed.samples.forEach { array.put(JSONObject().put("epochMs", it.epochMs)
            .put("lat", it.latitude).put("lon", it.longitude)) }
        val observations = JSONArray()
        lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("type") in setOf("movement", "eta") }
            .forEach(observations::put)
        return output.put("samples", array).put("eventsAndEta", observations).toString(2)
    }
    private fun readIndex() = runCatching { JSONArray(index.readText()) }.getOrDefault(JSONArray())
}

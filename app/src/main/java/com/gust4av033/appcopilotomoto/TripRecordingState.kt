package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object TripRecordingState {
    private val track = TripTrackAccumulator()
    var mapSession by mutableStateOf(TripMapSession())
        private set
    fun startMapSession(sessionId: String, elapsedMs: Long) {
        track.start(sessionId, elapsedMs); mapSession = track.snapshot
    }
    fun publishRecorded(sample: LocationSample, totalMeters: Double) {
        track.acceptRecorded(sample, totalMeters); mapSession = track.snapshot
    }
    fun finishMapSession(elapsedMs: Long) { track.finish(elapsedMs); mapSession = track.snapshot }
    var locationStatus by mutableStateOf("OFF")
        internal set
    var movement by mutableStateOf(MovementState.EN_MARCHA)
        internal set
    var samples by mutableStateOf(0)
        internal set
    var events by mutableStateOf(0)
        internal set
    var movingMs by mutableStateOf(0L)
        internal set
    var eta by mutableStateOf<EtaObservation?>(null)
        internal set
    fun clear() { track.clear(); mapSession = track.snapshot; locationStatus = "OFF"; movement = MovementState.EN_MARCHA; samples = 0; events = 0; movingMs = 0; eta = null }
}

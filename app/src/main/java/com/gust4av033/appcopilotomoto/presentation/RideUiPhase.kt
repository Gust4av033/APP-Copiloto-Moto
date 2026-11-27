package com.gust4av033.appcopilotomoto.presentation

enum class RideUiPhase { IDLE, RIDING, STOPPED_ON_ROUTE }

/** Presentation only. Repeated, missing or stale samples never prove a stop. */
data class RideUiPhaseMemory(
    val phase: RideUiPhase = RideUiPhase.IDLE,
    val candidateSinceMs: Long? = null,
    val lastSampleMs: Long? = null
)

fun rideUiPhase(previous: RideUiPhaseMemory, active: Boolean, speedKmh: Float?, sampleMs: Long?): RideUiPhaseMemory {
    if (!active) return RideUiPhaseMemory()
    val phase = if (previous.phase == RideUiPhase.IDLE) RideUiPhase.STOPPED_ON_ROUTE else previous.phase
    if (speedKmh == null || !speedKmh.isFinite() || speedKmh < 0 || sampleMs == null)
        return previous.copy(phase = phase, candidateSinceMs = null)
    if (previous.lastSampleMs != null && sampleMs <= previous.lastSampleMs)
        return previous.copy(phase = phase)
    val qualifying = if (phase == RideUiPhase.RIDING) speedKmh < 3f else speedKmh > 8f
    if (!qualifying) return RideUiPhaseMemory(phase, null, sampleMs)
    val contiguous = previous.lastSampleMs?.let { sampleMs - it <= 10_000 } == true
    val since = previous.candidateSinceMs?.takeIf { contiguous } ?: sampleMs
    val threshold = if (phase == RideUiPhase.RIDING) 5_000 else 3_000
    return if (sampleMs - since >= threshold)
        RideUiPhaseMemory(if (phase == RideUiPhase.RIDING) RideUiPhase.STOPPED_ON_ROUTE else RideUiPhase.RIDING, null, sampleMs)
    else RideUiPhaseMemory(phase, since, sampleMs)
}

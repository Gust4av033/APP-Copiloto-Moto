package com.gust4av033.appcopilotomoto

const val PRIVACY_TRIM_METERS = 300.0
data class TrimmedTrace(val samples: List<LocationSample>, val trimmedCompletely: Boolean)

/** Distance is accumulated along the trace, not as a radius around the home. */
object PrivacyTrim {
    fun apply(samples: List<LocationSample>, trimMeters: Double = PRIVACY_TRIM_METERS): TrimmedTrace {
        require(trimMeters >= 0)
        if (samples.size < 2) return TrimmedTrace(emptyList(), true)
        val cumulative = DoubleArray(samples.size)
        for (index in 1 until samples.size) cumulative[index] = cumulative[index - 1] +
            distanceMeters(samples[index - 1].latitude, samples[index - 1].longitude,
                samples[index].latitude, samples[index].longitude)
        val total = cumulative.last()
        if (total <= 2 * trimMeters) return TrimmedTrace(emptyList(), true)
        return TrimmedTrace(samples.indices.filter { cumulative[it] >= trimMeters &&
            total - cumulative[it] >= trimMeters }.map(samples::get), false)
    }
}

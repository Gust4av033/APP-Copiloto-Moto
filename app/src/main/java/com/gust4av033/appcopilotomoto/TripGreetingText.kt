package com.gust4av033.appcopilotomoto

import android.content.Context

internal fun TripGreetingDecision.spokenText(context: Context, starting: Boolean,
    name: String = ""): String {
    require(announcement == TripAnnouncement.VOICE)
    val safeName = TripGreetingSettings.normalizeName(name)
    val base = when {
        starting && safeName.isNotBlank() -> context.getString(R.string.trip_greeting_ready_named, safeName)
        starting -> context.getString(R.string.trip_greeting_ready)
        else -> context.getString(R.string.trip_greeting_finished)
    }
    val additions = if (starting) listOfNotNull(
        lowBatteryPercent?.let { context.getString(R.string.trip_greeting_low_battery, it) },
        if (gpsUnavailable) context.getString(R.string.trip_greeting_no_gps) else null)
    else listOfNotNull(if (distanceKilometers != null && durationMinutes != null)
        context.getString(R.string.trip_greeting_summary, distanceKilometers, durationMinutes) else null)
    return (listOf(base) + additions.take(2)).joinToString(" ")
}

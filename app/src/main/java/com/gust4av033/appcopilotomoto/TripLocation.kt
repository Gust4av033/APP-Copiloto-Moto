package com.gust4av033.appcopilotomoto

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

interface LocationSource {
    fun start(onSample: (LocationSample) -> Unit): Boolean
    fun stop()
}

class AndroidLocationSource(private val context: Context) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null
    @SuppressLint("MissingPermission") // available() checks precise permission and GPS; runCatching handles revocation races.
    override fun start(onSample: (LocationSample) -> Unit): Boolean {
        if (!TripLocationAccess.available(context) || listener != null) return false
        val observer = LocationListener { onSample(it.toTripSample()) }
        return runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, observer)
            listener = observer
            true
        }.getOrDefault(false)
    }
    override fun stop() { listener?.let { manager.removeUpdates(it) }; listener = null }
}

class ReplayLocationSource(private val fixture: List<LocationSample>) : LocationSource {
    override fun start(onSample: (LocationSample) -> Unit): Boolean {
        fixture.forEach { onSample(it.copy(provider = "replay_fixture", isSimulated = true)) }
        return true
    }
    override fun stop() = Unit
}

private fun Location.toTripSample() = LocationSample(time, elapsedRealtimeNanos / 1_000_000,
    latitude, longitude, altitude.takeIf { hasAltitude() }, speed.takeIf { hasSpeed() },
    if (Build.VERSION.SDK_INT >= 26 && hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearing.takeIf { hasBearing() },
    if (Build.VERSION.SDK_INT >= 26 && hasBearingAccuracy()) bearingAccuracyDegrees else null,
    accuracy.takeIf { hasAccuracy() }, provider ?: LocationManager.GPS_PROVIDER, false)

internal object TripLocationAccess {
    fun permission(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun gpsOn(context: Context) = runCatching { context.getSystemService(LocationManager::class.java)
        .isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    fun available(context: Context) = UserSettingsStore(context).tripLocationEnabled() && permission(context) && gpsOn(context)
    @SuppressLint("MissingPermission") // guarded by available(), including precise permission.
    fun currentFix(context: Context): LocationSample? {
        if (!available(context)) return null
        return runCatching { context.getSystemService(LocationManager::class.java)
            .getLastKnownLocation(LocationManager.GPS_PROVIDER)?.toTripSample() }
            .getOrNull()?.takeIf { SystemClock.elapsedRealtime() - it.elapsedRealtimeMs in 0..30_000 }
    }
}

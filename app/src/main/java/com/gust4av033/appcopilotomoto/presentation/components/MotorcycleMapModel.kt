package com.gust4av033.appcopilotomoto.presentation.components

import com.gust4av033.appcopilotomoto.LocationSample
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Camera policy ported from Copilot c0b10ad; callers supply a current, quality-gated fix. */
internal enum class TripMapCameraMode { FOLLOW_HEADING, NORTH_UP, FREE }
internal fun tripMapHeadingDegrees(fix: LocationSample?): Double? {
    if (fix == null || fix.speedMetersPerSec?.let { it.isFinite() && it >= 2f } != true) return null
    if (fix.bearingAccuracyDegrees?.let { it.isFinite() && it in 0f..30f } != true) return null
    return fix.bearing?.takeIf { it.isFinite() && it in 0f..360f }?.toDouble()
}

/** Original stylised road bike. Actual prisms share the map's camera/depth buffer.
 * Display scale exaggerates size for legibility; this is an avatar, not vehicle dimensions.
 * Coordinates are rebuilt only for a new fix or after a zoom gesture, never each frame. */
internal object MotorcycleMapModel {
    fun geoJson(latitude: Double, longitude: Double, heading: Double, scale: Double): String {
        require(latitude in -TripMapStyle.MAX_LATITUDE..TripMapStyle.MAX_LATITUDE)
        require(longitude in -180.0..180.0 && heading.isFinite() && scale.isFinite() && scale > 0)
        val features = JSONArray()
        val angle = Math.toRadians(heading)
        val longitudeFactor = 111_320.0 * cos(Math.toRadians(latitude))
        fun prism(points: List<Pair<Double, Double>>, base: Double, height: Double, color: String) {
            val ring = JSONArray()
            (points + points.first()).forEach { (x, y) ->
                val east = (x * cos(angle) + y * sin(angle)) * scale
                val north = (y * cos(angle) - x * sin(angle)) * scale
                val lon = ((longitude + east / longitudeFactor + 540) % 360) - 180
                ring.put(JSONArray().put(lon).put((latitude + north / 111_320.0)
                    .coerceIn(-TripMapStyle.MAX_LATITUDE, TripMapStyle.MAX_LATITUDE)))
            }
            features.put(JSONObject().put("type", "Feature")
                .put("properties", JSONObject().put("base", base * scale).put("height", height * scale).put("color", color))
                .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))))
        }
        fun box(x: Double, y: Double, width: Double, length: Double, base: Double, height: Double, color: String) =
            prism(listOf(x-width/2 to y-length/2, x+width/2 to y-length/2,
                x+width/2 to y+length/2, x-width/2 to y+length/2), base, height, color)
        // Wheels stand vertically: stacked chords approximate circular tyres and metal hubs.
        for (y in listOf(-.72, .72)) {
            for (slice in 0 until 10) {
                val low = slice * .064
                val length = 2 * sqrt(.32.pow(2) - (low + .032 - .32).pow(2))
                box(0.0, y, .16, length, low, low + .064, Glass.motoRubber)
                if (slice in 2..7) box(0.0, y, .17, length * .67, low, low + .064, Glass.motoMetal)
            }
        }
        box(0.0, -.1, .36, .85, .3, .55, Glass.motoMetal) // engine
        box(0.0, -.1, .25, 1.2, .52, .62, Glass.motoRubber) // frame
        box(.25, -.46, .12, .85, .23, .34, Glass.motoMetal) // exhaust
        box(0.0, -.57, .33, .68, .67, .79, Glass.motoBlue) // tail fairing
        box(0.0, -.4, .35, .7, .8, .88, Glass.motoRubber) // seat
        prism(listOf(-.22 to -.07, .22 to -.07, .25 to .22, .16 to .48,
            -.16 to .48, -.25 to .22), .62, .97, Glass.motoBlue) // tank
        box(0.0, .18, .12, .44, .97, .99, Glass.motoTrim)
        for (x in listOf(-.12, .12)) box(x, .66, .06, .12, .31, 1.0, Glass.motoMetal) // fork
        box(0.0, .72, .22, .36, .67, .73, Glass.motoBlue) // front fender
        box(0.0, .59, .4, .2, .91, 1.13, Glass.motoBlue) // headlight cowl
        box(0.0, .71, .3, .04, .95, 1.08, Glass.motoLight)
        box(0.0, .45, .73, .06, 1.05, 1.11, Glass.motoMetal) // handlebar
        for (x in listOf(-.34, .34)) {
            box(x, .45, .15, .1, 1.05, 1.12, Glass.motoRubber)
            box(x, .53, .025, .025, 1.11, 1.28, Glass.motoMetal)
            box(x, .53, .14, .07, 1.25, 1.31, Glass.motoMetal)
        }
        box(0.0, -.92, .24, .05, .68, .76, Glass.motoTail)
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
}

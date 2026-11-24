package com.gust4av033.appcopilotomoto.presentation.components

import com.gust4av033.appcopilotomoto.TripMapSession
import com.gust4av033.appcopilotomoto.TripTilePolicy
import org.json.JSONArray
import org.json.JSONObject

internal data class TripMapPaint(val background: String, val route: String, val current: String,
    val stale: String, val rim: String, val lineWidth: Float, val pointRadius: Float, val rimWidth: Float, val brightness: Float)

internal object TripMapStyle {
    const val ROUTE = "trip-recorded-route"
    const val FIX = "trip-observed-fix"
    const val OSM = "osm-standard"
    const val MAX_LATITUDE = 85.05112878
    fun emptyCollection() = JSONObject().put("type", "FeatureCollection").put("features", JSONArray())
    fun route(session: TripMapSession): String {
        val lines = JSONArray()
        session.segments.filter { it.size >= 2 }.forEach { segment ->
            lines.put(JSONArray().also { line -> segment.forEach { line.put(JSONArray().put(it.longitude).put(it.latitude)) } })
        }
        return JSONObject().put("type", "Feature").put("properties", JSONObject()).put("geometry",
            JSONObject().put("type", "MultiLineString").put("coordinates", lines)).toString()
    }
    fun fix(session: TripMapSession): String = session.lastFix?.value?.let {
        JSONObject().put("type", "Feature").put("properties", JSONObject()).put("geometry",
            JSONObject().put("type", "Point").put("coordinates", JSONArray().put(it.longitude).put(it.latitude))).toString()
    } ?: emptyCollection().toString()
    fun json(online: Boolean, paint: TripMapPaint): String {
        val sources = JSONObject()
        for (id in listOf(ROUTE, FIX)) sources.put(id,
            JSONObject().put("type", "geojson").put("data", emptyCollection()))
        val layers = JSONArray().put(JSONObject().put("id", "background").put("type", "background")
            .put("paint", JSONObject().put("background-color", paint.background)))
        if (online) {
            sources.put(OSM, JSONObject().put("type", "raster").put("tileSize", 256).put("minzoom", 0)
                .put("maxzoom", 19).put("tiles", JSONArray().put(TripTilePolicy.TEMPLATE))
                .put("attribution", "© OpenStreetMap contributors"))
            layers.put(JSONObject().put("id", "streets").put("type", "raster").put("source", OSM)
                .put("paint", JSONObject().put("raster-fade-duration", 0).put("raster-brightness-max", paint.brightness)))
        }
        layers.put(JSONObject().put("id", ROUTE).put("type", "line").put("source", ROUTE)
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put("paint", JSONObject().put("line-color", paint.route).put("line-width", paint.lineWidth)))
        layers.put(JSONObject().put("id", FIX).put("type", "circle").put("source", FIX)
            .put("paint", JSONObject().put("circle-color", paint.current).put("circle-radius", paint.pointRadius)
                .put("circle-stroke-color", paint.rim).put("circle-stroke-width", paint.rimWidth)))
        return JSONObject().put("version", 8).put("sources", sources).put("layers", layers)
            .put("transition", JSONObject().put("duration", 0).put("delay", 0)).toString()
    }
}

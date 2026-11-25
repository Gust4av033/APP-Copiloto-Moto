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
        return json(online, paint, null)
    }
    fun json(online: Boolean, paint: TripMapPaint, base: String?): String {
        val sources = JSONObject()
        val layers = JSONArray()
        val result = if (online && base != null) runCatching { JSONObject(base) }.getOrElse {
            JSONObject().put("version", 8).put("sources", JSONObject()).put("layers", JSONArray())
        } else JSONObject().put("version", 8).put("sources", JSONObject()).put("layers",
            JSONArray().put(JSONObject().put("id", "background").put("type", "background")
                .put("paint", JSONObject().put("background-color", paint.background))))
        val resultSources = result.optJSONObject("sources") ?: JSONObject()
        val resultLayers = result.optJSONArray("layers") ?: JSONArray()
        for (id in listOf(ROUTE, FIX)) resultSources.put(id,
            JSONObject().put("type", "geojson").put("data", emptyCollection()))
        resultLayers.put(JSONObject().put("id", ROUTE).put("type", "line").put("source", ROUTE)
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put("paint", JSONObject().put("line-color", paint.route).put("line-width", paint.lineWidth)))
        resultLayers.put(JSONObject().put("id", FIX).put("type", "circle").put("source", FIX)
            .put("paint", JSONObject().put("circle-color", paint.current).put("circle-radius", paint.pointRadius)
                .put("circle-stroke-color", paint.rim).put("circle-stroke-width", paint.rimWidth)))
        result.put("sources", resultSources).put("layers", resultLayers)
        result.put("transition", JSONObject().put("duration", 0).put("delay", 0))
        if (!online) {
            resultSources.put(ROUTE, JSONObject().put("type", "geojson").put("data", emptyCollection()))
            resultSources.put(FIX, JSONObject().put("type", "geojson").put("data", emptyCollection()))
        }
        return result.toString()
    }
}

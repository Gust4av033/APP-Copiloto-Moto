package com.gust4av033.appcopilotomoto.presentation.components

import com.gust4av033.appcopilotomoto.TripMapSession
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import org.json.JSONArray
import org.json.JSONObject

internal data class TripMapPaint(val background: String, val route: String, val current: String,
    val stale: String, val rim: String, val lineWidth: Float, val pointRadius: Float, val rimWidth: Float, val brightness: Float,
    val land: String, val coastline: String)

internal object TripMapStyle {
    const val ROUTE = "trip-recorded-route"
    const val FIX = "trip-observed-fix"
    const val LAND = "world-land"
    const val VECTOR = "openmaptiles"
    const val TREES = "map-stylised-vegetation"
    const val GLOW = "trip-route-glow"
    const val MOTO = "trip-motorcycle-3d"
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
    private fun applyNightPalette(style: JSONObject) {
        val layers = style.optJSONArray("layers") ?: return
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val id = layer.getString("id")
            val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
            val palette = Glass.MapNight
            when (layer.getString("type")) {
                "background" -> paint.put("background-color", palette.background)
                "raster" -> paint.put("raster-brightness-max", .3)
                "fill" -> {
                    paint.put("fill-color", when {
                        id.contains("water") -> palette.water
                        id.contains("building") -> palette.building
                        id.contains("park") || id.contains("wood") || id.contains("grass") -> palette.park
                        else -> palette.land
                    })
                    if (paint.has("fill-outline-color")) paint.put("fill-outline-color", palette.edge)
                }
                "fill-extrusion" -> paint.put("fill-extrusion-color", palette.building)
                "line" -> paint.put("line-color", when {
                    id.contains("water") -> palette.water
                    id.contains("casing") -> palette.edge
                    id.contains("motorway") || id.contains("trunk") || id.contains("primary") -> palette.mainRoad
                    else -> palette.road
                })
                "symbol" -> if (layer.optJSONObject("layout")?.has("text-field") == true) {
                    paint.put("text-color", when {
                        id.contains("water") -> palette.waterLabel
                        id.startsWith("poi") -> palette.minorLabel
                        else -> palette.label
                    }).put("text-halo-color", palette.background).put("text-halo-width", 1.0)
                }
            }
        }
    }
    /** Only explicit OMT oneway values establish direction; never infer it from the trace. */
    internal fun roadDirection(value: Int): Int? = when (value) { 1 -> 0; -1 -> 180; else -> null }
    private fun roadLegibility(style: JSONObject) {
        val original = style.optJSONArray("layers") ?: return
        val layers = JSONArray()
        for (i in 0 until original.length()) {
            val layer = original.getJSONObject(i)
            if (layer.getString("id").startsWith("road_one_way_arrow")) continue
            if (layer.getString("id").startsWith("highway-name-")) {
                layer.getJSONObject("layout")
                    .put("text-field", JSONArray("[\"coalesce\",[\"get\",\"name:es\"],[\"get\",\"name\"],[\"get\",\"name:latin\"]]"))
                    .put("text-size", JSONArray("[\"interpolate\",[\"linear\"],[\"zoom\"],13,12,16,15,19,17]"))
                layer.getJSONObject("paint").put("text-halo-width", 1.5)
            }
            layers.put(layer)
        }
        for (direction in listOf(1, -1)) layers.put(JSONObject()
            .put("id", "app-road-direction-$direction").put("type", "symbol")
            .put("source", VECTOR).put("source-layer", "transportation").put("minzoom", 16)
            .put("filter", JSONArray().put("all")
                .put(JSONArray().put("==").put(JSONArray().put("get").put("oneway")).put(direction))
                .put(JSONArray("[\"match\",[\"get\",\"class\"],[\"motorway\",\"trunk\",\"primary\",\"secondary\",\"tertiary\",\"minor\",\"service\"],true,false]")))
            .put("layout", JSONObject().put("symbol-placement", "line").put("symbol-spacing", 120)
                .put("icon-image", "app-road-direction").put("icon-size", .75)
                .put("icon-rotate", roadDirection(direction)).put("icon-rotation-alignment", "map")
                .put("icon-pitch-alignment", "map").put("icon-keep-upright", false)
                .put("icon-allow-overlap", false).put("icon-ignore-placement", false)))
        style.put("layers", layers)
    }
    fun json(online: Boolean, paint: TripMapPaint, base: String? = null, version: com.gust4av033.appcopilotomoto.MapVectorVersion? = null): String {
        val result = if (online && base != null) JSONObject(base) else JSONObject()
        if (online && paint.brightness < 1f) applyNightPalette(result)
        if (online && base != null) roadLegibility(result)
        val sources = result.optJSONObject("sources") ?: JSONObject()
        if (online && version != null) sources.put(VECTOR, version.source())
        for (id in listOf(ROUTE, FIX, MOTO, TREES)) sources.put(id,
            JSONObject().put("type", "geojson").put("data", emptyCollection()))
        val layers = result.optJSONArray("layers") ?: JSONArray().put(JSONObject().put("id", "background").put("type", "background")
            .put("paint", JSONObject().put("background-color", paint.background)))
        // Small-scale geography bundled with the app. Hide it at street zoom.
        if (!online || base == null) {
        sources.put(LAND, JSONObject().put("type", "geojson").put("data", "asset://maps/land-110m.geojson"))
        layers.put(JSONObject().put("id", LAND).put("type", "fill").put("source", LAND).put("maxzoom", 7)
            .put("paint", JSONObject().put("fill-color", paint.land).put("fill-outline-color", paint.coastline)))
        }
        layers.put(JSONObject().put("id", GLOW).put("type", "line").put("source", ROUTE)
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put("paint", JSONObject().put("line-color", paint.route).put("line-width", Glass.routeGlowWidth)
                .put("line-opacity", Glass.routeGlowOpacity)))
        layers.put(JSONObject().put("id", TREES).put("type", "fill-extrusion").put("source", TREES).put("minzoom", 15)
            .put("layout", JSONObject().put("visibility", "none"))
            .put("paint", JSONObject().put("fill-extrusion-color", JSONArray().put("get").put("color"))
                .put("fill-extrusion-base", JSONArray().put("get").put("base"))
                .put("fill-extrusion-height", JSONArray().put("get").put("height"))))
        layers.put(JSONObject().put("id", ROUTE).put("type", "line").put("source", ROUTE)
            .put("layout", JSONObject().put("line-cap", "round").put("line-join", "round"))
            .put("paint", JSONObject().put("line-color", paint.route).put("line-width", paint.lineWidth)))
        layers.put(JSONObject().put("id", FIX).put("type", "circle").put("source", FIX)
            .put("paint", JSONObject().put("circle-color", paint.current).put("circle-radius", paint.pointRadius)
                .put("circle-stroke-color", paint.rim).put("circle-stroke-width", paint.rimWidth)))
        layers.put(JSONObject().put("id", MOTO).put("type", "fill-extrusion").put("source", MOTO)
            .put("minzoom", 14).put("paint", JSONObject()
                .put("fill-extrusion-color", JSONArray().put("get").put("color"))
                .put("fill-extrusion-base", JSONArray().put("get").put("base"))
                .put("fill-extrusion-height", JSONArray().put("get").put("height"))
                .put("fill-extrusion-opacity", 1)))
        return result.put("version", 8).put("sources", sources).put("layers", layers)
            .put("transition", JSONObject().put("duration", 0).put("delay", 0)).toString()
    }
}

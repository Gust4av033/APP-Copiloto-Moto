package com.gust4av033.appcopilotomoto.presentation.components

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.*

internal data class MapPoint(val x: Double, val y: Double)
internal data class MapBounds(val west: Double, val south: Double, val east: Double, val north: Double)
internal data class MapPolygon(val rings: List<List<MapPoint>>) {
    private val shell = rings.first()
    private val west = shell.minOf { it.x }; private val east = shell.maxOf { it.x }
    private val south = shell.minOf { it.y }; private val north = shell.maxOf { it.y }
    fun contains(point: MapPoint): Boolean {
        if (point.x !in west..east || point.y !in south..north) return false
        fun inside(ring: List<MapPoint>): Boolean {
            var hit = false
            var previous = ring.last()
            for (current in ring) {
                if ((current.y > point.y) != (previous.y > point.y) &&
                    point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) hit = !hit
                previous = current
            }
            return hit
        }
        return inside(shell) && rings.drop(1).none(::inside)
    }
}

/** Decorative canopies inside mapped green polygons, not a database of individual trees.
 * Stable world grid, holes/roads/water/buildings excluded, bounded work and no per-frame generation. */
internal object MapVegetation {
    private const val R = 6378137.0
    const val MAX_TREES = 64
    fun point(lon: Double, lat: Double) = MapPoint(R * Math.toRadians(lon),
        R * ln(tan(PI / 4 + Math.toRadians(lat.coerceIn(-85.0, 85.0)) / 2)))
    private fun coordinates(point: MapPoint) = JSONArray().put(Math.toDegrees(point.x / R))
        .put(Math.toDegrees(2 * atan(exp(point.y / R)) - PI / 2))
    fun polygons(raw: List<String>): List<MapPolygon> = raw.distinct().take(32).flatMap { json -> runCatching {
        val geometry = JSONObject(json).optJSONObject("geometry") ?: return@runCatching emptyList()
        val coordinates = geometry.getJSONArray("coordinates")
        val polygons = if (geometry.getString("type") == "Polygon") listOf(coordinates)
            else if (geometry.getString("type") == "MultiPolygon") (0 until coordinates.length()).map { coordinates.getJSONArray(it) }
            else emptyList()
        polygons.mapNotNull { polygon ->
            val rings = (0 until polygon.length()).map { i ->
                val ring = polygon.getJSONArray(i)
                require(ring.length() <= 2048)
                (0 until ring.length()).map { j -> ring.getJSONArray(j).let { point(it.getDouble(0), it.getDouble(1)) } }
            }
            if (rings.isEmpty() || rings.any { it.size < 3 || it.any { p -> !p.x.isFinite() || !p.y.isFinite() } }) null
            else MapPolygon(rings)
        }
    }.getOrDefault(emptyList()) }
    fun paths(raw: List<String>): List<List<MapPoint>> = raw.take(256).flatMap { json -> runCatching {
        val geometry = JSONObject(json).optJSONObject("geometry") ?: return@runCatching emptyList()
        val coordinates = geometry.getJSONArray("coordinates")
        val lines = when (geometry.getString("type")) {
            "LineString" -> listOf(coordinates)
            "MultiLineString" -> (0 until coordinates.length()).map { coordinates.getJSONArray(it) }
            else -> emptyList()
        }
        lines.map { line -> (0 until line.length().coerceAtMost(2048)).map { i ->
            line.getJSONArray(i).let { point(it.getDouble(0), it.getDouble(1)) }
        } }
    }.getOrDefault(emptyList()) }
    private fun roadDistance(p: MapPoint, a: MapPoint, b: MapPoint): Double {
        val dx = b.x-a.x; val dy = b.y-a.y
        val t = if (dx*dx+dy*dy == 0.0) 0.0 else (((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)
        return hypot(p.x-a.x-t*dx, p.y-a.y-t*dy)
    }
    fun build(green: List<MapPolygon>, occupied: List<MapPolygon>, roads: List<List<MapPoint>>,
        bounds: MapBounds, zoom: Double, dark: Boolean): String {
        val empty = TripMapStyle.emptyCollection()
        if (zoom < 15 || green.isEmpty() || bounds.west >= bounds.east || bounds.south >= bounds.north) return empty.toString()
        val low = point(bounds.west, bounds.south); val high = point(bounds.east, bounds.north)
        if (high.x-low.x > 5000 || high.y-low.y > 5000) return empty.toString()
        val spacing = 18 * 2.0.pow((17 - floor(zoom)).coerceAtLeast(0.0))
        val radius = (spacing * .19).coerceIn(3.0, 9.0)
        val candidates = buildList {
            val minX = floor(low.x/spacing).toInt(); val maxX = ceil(high.x/spacing).toInt()
            val minY = floor(low.y/spacing).toInt(); val maxY = ceil(high.y/spacing).toInt()
            if ((maxX-minX).toLong() * (maxY-minY) > 2400) return empty.toString()
            for (x in minX..maxX) for (y in minY..maxY) add(MapPoint(x*spacing, y*spacing))
        }.sortedBy { hypot(it.x-(low.x+high.x)/2, it.y-(low.y+high.y)/2) }
        val roadEdges = roads.asSequence().flatMap { it.zipWithNext().asSequence() }.take(1024).toList()
        val trees = candidates.asSequence().filter { p ->
            val outline = listOf(p, MapPoint(p.x-radius,p.y), MapPoint(p.x+radius,p.y),
                MapPoint(p.x,p.y-radius), MapPoint(p.x,p.y+radius))
            outline.all { q -> green.any { it.contains(q) } && occupied.none { it.contains(q) } } &&
                roadEdges.none { (a,b) -> roadDistance(p,a,b) < radius+4 }
        }.take(MAX_TREES).toList()
        val features = JSONArray()
        fun prism(center: MapPoint, radius: Double, base: Double, height: Double, color: String, index: Int) {
            val ring = JSONArray()
            for (i in 0 until 6) ring.put(coordinates(MapPoint(center.x+cos(i*PI/3)*radius, center.y+sin(i*PI/3)*radius)))
            ring.put(ring.getJSONArray(0))
            features.put(JSONObject().put("type", "Feature").put("properties", JSONObject()
                .put("tree", index).put("base", base).put("height", height).put("color", color))
                .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray().put(ring))))
        }
        trees.forEachIndexed { i,p ->
            val body = if (dark) Glass.treeNight else Glass.treeDay
            val top = if (dark) Glass.treeNightTop else Glass.treeDayTop
            prism(p, radius*.13, 0.0, radius, Glass.treeTrunk, i)
            prism(p, radius, radius*.7, radius*1.5, body, i)
            prism(p, radius*.78, radius*1.5, radius*2.1, body, i)
            prism(p, radius*.43, radius*2.1, radius*2.6, top, i)
        }
        return empty.put("features", features).toString()
    }
}

/** Collect only visible cartographic features. Geometry work uses a cancellable background job. */
internal class NativeVegetation(private val map: MapLibreMap, private val width: () -> Int,
    private val height: () -> Int, private val dark: Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var active = false
    private var generation = 0
    private var signature: Int? = null
    private var previousAt = 0L
    private val probe = Runnable { collect() }
    fun visible(value: Boolean) {
        if (active == value) return
        active = value; generation++; job?.cancel(); handler.removeCallbacks(probe)
        if (value) { signature = null; request() }
    }
    fun request() {
        if (!active) return
        handler.removeCallbacks(probe); handler.postDelayed(probe, 250)
    }
    private fun collect() {
        if (!active || map.cameraPosition.zoom < 15 || map.style?.isFullyLoaded != true) return
        val now = SystemClock.elapsedRealtime()
        if (now - previousAt < 1500) { handler.postDelayed(probe, 1500 - (now-previousAt)); return }
        previousAt = now
        val box = RectF(0f, 0f, width().toFloat(), height().toFloat())
        fun features(ids: List<String>) = map.queryRenderedFeatures(box, *ids.toTypedArray()).map { it.toJson() }
        val green = features(listOf("park", "landcover_wood", "landcover_grass"))
        val excluded = features(listOf("water", "building", "building-3d", "landuse_pitch", "landuse_track", "aeroway_fill", "road_area_pattern"))
        val roads = features(map.style!!.layers.map { it.id }.filter { it.startsWith("road_") || it.startsWith("bridge_") })
        val bounds = map.projection.visibleRegion.latLngBounds
        val limits = MapBounds(bounds.longitudeWest, bounds.latitudeSouth, bounds.longitudeEast, bounds.latitudeNorth)
        val zoom = map.cameraPosition.zoom
        val nextSignature = listOf(green.toSet(), excluded.toSet(), roads.toSet(), limits, floor(zoom)).hashCode()
        if (signature == nextSignature) return
        signature = nextSignature
        val token = ++generation; job?.cancel()
        job = scope.launch {
            val json = withContext(Dispatchers.Default) {
                MapVegetation.build(MapVegetation.polygons(green), MapVegetation.polygons(excluded), MapVegetation.paths(roads), limits, zoom, dark)
            }
            if (active && token == generation) map.style?.getSourceAs<GeoJsonSource>(TripMapStyle.TREES)?.setGeoJson(json)
        }
    }
    fun destroy() { visible(false); scope.cancel() }
}

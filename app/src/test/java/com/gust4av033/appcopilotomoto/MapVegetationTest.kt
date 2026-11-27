package com.gust4av033.appcopilotomoto

import com.gust4av033.appcopilotomoto.presentation.components.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapVegetationTest {
    private val bounds = MapBounds(-.002, -.002, .002, .002)
    private fun ring(w: Double, s: Double, e: Double, n: Double) =
        JSONArray("[[$w,$s],[$e,$s],[$e,$n],[$w,$n],[$w,$s]]")
    private fun polygon(vararg rings: JSONArray) = JSONObject().put("type", "Feature")
        .put("geometry", JSONObject().put("type", "Polygon").put("coordinates", JSONArray(rings.toList()))).toString()
    private val green = polygon(ring(-.003, -.003, .003, .003))
    private fun build(polygons: List<String> = listOf(green), occupied: List<String> = emptyList(),
        roads: List<String> = emptyList(), zoom: Double = 18.0, box: MapBounds = bounds) =
        MapVegetation.build(MapVegetation.polygons(polygons), MapVegetation.polygons(occupied),
            MapVegetation.paths(roads), box, zoom, true)
    private fun features(json: String) = JSONObject(json).getJSONArray("features")
    @Test fun smallBuildingsInsideCanopiesAndMissingExclusionsSuppressDecoration() {
        val shape = MapPolygon(listOf(listOf(MapPoint(1.0,1.0), MapPoint(2.0,1.0), MapPoint(2.0,2.0), MapPoint(1.0,2.0))))
        assertTrue(shape.overlapsDisk(MapPoint(0.0, 0.0), 3.0))
        assertFalse(shape.contains(MapPoint(0.0, 0.0)))
        assertEquals(0, features(MapVegetation.buildFromFeatures(listOf(green), listOf("broken"), emptyList(), bounds, 18.0, true)).length())
        val partial = JSONObject(green)
        partial.getJSONObject("geometry").put("type", "MultiPolygon").put("coordinates",
            JSONArray().put(JSONArray().put(ring(-.002, -.002, -.001, -.001))).put(JSONArray().put(JSONArray())))
        assertEquals(0, features(MapVegetation.buildFromFeatures(listOf(green), listOf(partial.toString()), emptyList(), bounds, 18.0, true)).length())
        val many = (1..33).map { polygon(ring(it*.00001, -.001, it*.00001+.000001, .001)) }
        assertEquals(0, features(MapVegetation.buildFromFeatures(listOf(green), many, emptyList(), bounds, 18.0, true)).length())
    }
    @Test fun noGreenCoverageOrLowZoomNeverInventsForest() {
        assertEquals(0, features(build(polygons = emptyList())).length())
        assertEquals(0, features(build(polygons = listOf("broken"))).length())
        assertEquals(0, features(build(zoom = 14.9)).length())
        assertEquals(0, features(build(box = MapBounds(-2.0, -2.0, 2.0, 2.0))).length())
    }
    @Test fun forestIsDeterministicBoundedAndProducesClosedThreeDimensionalPrisms() {
        val first = build()
        assertEquals(first, build())
        val data = features(first)
        assertTrue(data.length() > 0)
        assertTrue(data.length() <= MapVegetation.MAX_TREES * 4)
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val props = item.getJSONObject("properties")
            assertTrue(props.getDouble("height") > props.getDouble("base"))
            val ring = item.getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
            assertEquals(ring.getJSONArray(0).toString(), ring.getJSONArray(ring.length()-1).toString())
        }
    }
    @Test fun mappedHolesWaterBuildingsAndRoadsStayClear() {
        val hole = ring(-.0004, -.0004, .0004, .0004)
        val park = polygon(ring(-.003, -.003, .003, .003), hole)
        val water = polygon(ring(-.002, -.002, -.0006, .002))
        val road = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[0.0009,-0.003],[0.0009,0.003]]}}"""
        val data = features(build(listOf(park), listOf(water), listOf(road)))
        assertTrue(data.length() > 0)
        val greenShape = MapVegetation.polygons(listOf(park)).single()
        val waterShape = MapVegetation.polygons(listOf(water)).single()
        val roadX = MapVegetation.point(.0009, 0.0).x
        for (i in 0 until data.length()) {
            val ring = data.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0)
            for (j in 0 until ring.length()) {
                val xy = ring.getJSONArray(j)
                val point = MapVegetation.point(xy.getDouble(0), xy.getDouble(1))
                assertTrue(greenShape.contains(point))
                assertFalse(waterShape.contains(point))
                assertTrue(kotlin.math.abs(point.x - roadX) >= 4)
            }
        }
        assertEquals(0, features(build(occupied = listOf(green))).length())
    }
}

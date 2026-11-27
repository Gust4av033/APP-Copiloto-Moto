package com.gust4av033.appcopilotomoto

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal data class MapVectorVersion(val version: String, val tileTemplate: String, val minZoom: Int, val maxZoom: Int) {
    fun source() = JSONObject().put("type", "vector").put("tiles", JSONArray().put(tileTemplate))
        .put("minzoom", minZoom).put("maxzoom", maxZoom)
        .put("attribution", TripTilePolicy.OPEN_FREE_MAP.attributionLabel)
}

internal object MapCatalogPolicy {
    const val LATEST = "https://tiles.openfreemap.org/planet/latest"
    const val MAX_BYTES = 256 * 1024L
    private val template = Regex("https://tiles\\.openfreemap\\.org/planet/([0-9]{8}_[0-9]{6}_pt)/\\{z}/\\{x}/\\{y}\\.pbf")
    fun parse(json: String): MapVectorVersion {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(json)
        require(root.getString("tilejson").startsWith("3.") || root.getString("tilejson").startsWith("2."))
        require(root.optString("scheme", "xyz") == "xyz")
        val tiles = root.getJSONArray("tiles"); require(tiles.length() == 1)
        val url = tiles.getString(0)
        val version = requireNotNull(template.matchEntire(url)).groupValues[1]
        LocalDateTime.parse(version, DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss'_pt'").withResolverStyle(ResolverStyle.STRICT))
        val min = root.getInt("minzoom"); val max = root.getInt("maxzoom")
        require(min == 0 && root.getDouble("minzoom") == 0.0 && max in 0..14 && root.getDouble("maxzoom") == max.toDouble())
        val layers = root.getJSONArray("vector_layers")
        require(layers.length() <= 64)
        val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }.toSet()
        require(ids.containsAll(setOf("building", "water", "transportation", "landcover")))
        return MapVectorVersion(version, url, min, max)
    }
}

internal enum class MapCatalogStatus { SAVED, UPDATED, UNCHANGED, PREVIOUS, FAILED, WAIT }
internal data class MapCatalogResult(val version: MapVectorVersion?, val status: MapCatalogStatus)
internal object RevalidateMapCatalog

/** One small TileJSON, never a region download. Keep the last validated version on every failure. */
internal class TripMapCatalog(context: Context, private val client: OkHttpClient,
    private val consent: () -> Boolean, private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val preferences = context.applicationContext.getSharedPreferences("map_catalog", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastAttempt: Long? = null
    private var lastSuccess: Long? = null
    fun saved(): MapVectorVersion? = runCatching {
        MapCatalogPolicy.parse(requireNotNull(preferences.getString("validated_tilejson", null)))
    }.getOrNull()

    suspend fun resolve(force: Boolean): MapCatalogResult = mutex.withLock {
        val previous = saved()
        if (!consent()) return@withLock MapCatalogResult(previous, MapCatalogStatus.FAILED)
        val now = clock()
        if (!force && previous != null && lastSuccess?.let { now - it in 0 until 3_600_000 } == true)
            return@withLock MapCatalogResult(previous, MapCatalogStatus.SAVED)
        if (lastAttempt?.let { now - it in 0 until 60_000 } == true)
            return@withLock MapCatalogResult(previous, MapCatalogStatus.WAIT)
        lastAttempt = now
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(MapCatalogPolicy.LATEST).apply {
                    if (force) tag(RevalidateMapCatalog::class.java, RevalidateMapCatalog)
                }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Map metadata unavailable")
                    val body = response.body ?: throw IOException("Empty metadata")
                    require(body.contentLength() <= MapCatalogPolicy.MAX_BYTES)
                    val bytes = body.byteStream().use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (buffer.size() <= MapCatalogPolicy.MAX_BYTES) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            buffer.write(chunk, 0, count)
                        }
                        buffer.toByteArray()
                    }
                    require(bytes.size <= MapCatalogPolicy.MAX_BYTES)
                    val json = bytes.toString(Charsets.UTF_8)
                    val next = MapCatalogPolicy.parse(json)
                    currentCoroutineContext().ensureActive()
                    check(consent())
                    if (previous != null && next.version < previous.version)
                        MapCatalogResult(previous, MapCatalogStatus.PREVIOUS)
                    else {
                        preferences.edit().putString("validated_tilejson", json).apply()
                        lastSuccess = clock()
                        MapCatalogResult(next, if (next == previous) MapCatalogStatus.UNCHANGED else MapCatalogStatus.UPDATED)
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { MapCatalogResult(previous, MapCatalogStatus.FAILED) }
    }
}

package com.gust4av033.appcopilotomoto.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gust4av033.appcopilotomoto.*
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import org.maplibre.android.MapLibre
import org.maplibre.android.RenderingEngine
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.*
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import java.util.Locale

/** Test hosts may supply an explicitly labelled renderer; production always uses MapLibre. */
fun interface TripMapRenderer {
    @Composable fun Render(session: TripMapSession, now: Long, online: Boolean)
}
val LocalTripMapRenderer = staticCompositionLocalOf<TripMapRenderer> { TripMapRenderer { session, now, online ->
    NativeTripMap(session, now, online)
} }

internal class NativeTripMapController(context: Context, first: TripMapSession, private val online: Boolean,
    private val paint: TripMapPaint, private val report: (Int) -> Unit) {
    private val token = Any()
    private var destroyed = false
    private var visible = false
    private var lifecycle = Lifecycle.State.CREATED
    private var started = false
    private var resumed = false
    private var following = true
    private var map: MapLibreMap? = null
    private var data = first
    private var now = 0L
    private var lastApplied: TripMapSession? = null
    private var lastFresh: Boolean? = null
    val view: MapView
    init {
        MapLibre.getInstance(context.applicationContext, null, WellKnownTileServer.MapLibre, RenderingEngine.Type.OPENGL)
        HttpRequestUtil.setOkHttpClient(TripMapNetwork.client(context))
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        val fix = checkNotNull(first.lastFix).value
        view = MapView(context, MapLibreMapOptions().textureMode(true).actionJournalEnabled(false)
            .camera(CameraPosition.Builder().target(LatLng(fix.latitude, fix.longitude)).zoom(16.0).build())
            .setPrefetchesTiles(false).setPrefetchZoomDelta(0).minZoomPreference(2.0).maxZoomPreference(19.0)
            .tiltGesturesEnabled(false).rotateGesturesEnabled(false).compassEnabled(false)
            .logoEnabled(false).attributionEnabled(false)) // Compose draws the persistent linked attribution over the map.
        view.onCreate(null)
        view.setMaximumFps(30)
        view.addOnDidFailLoadingMapListener { if (!destroyed) report(R.string.trip_map_load_error) }
        view.addOnRenderErrorListener { if (!destroyed) report(R.string.trip_map_render_error) }
        view.addOnTileActionListener { operation, _, _, _, _, _, _ ->
            if (!destroyed && online) when (operation) {
                TileOperation.Error -> report(R.string.trip_map_tile_error)
                TileOperation.LoadFromCache, TileOperation.LoadFromNetwork -> report(R.string.trip_map_tiles_ready)
                else -> Unit
            }
        }
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            ready.setPrefetchesTiles(false); ready.setPrefetchZoomDelta(0)
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
            }
            val base = if (online) runCatching {
                context.assets.open(if (paint.brightness < 1f) "map/liberty_dark.json" else "map/liberty.json")
                    .bufferedReader().use { it.readText() }
            }.getOrNull() else null
            ready.setStyle(Style.Builder().fromJson(TripMapStyle.json(online, paint, base))) {
                if (!destroyed) {
                    lastApplied = null; applyData()
                    report(if (online) R.string.trip_map_loading_tiles else R.string.trip_map_local_only)
                }
            }
        }
    }
    fun update(session: TripMapSession, elapsedMs: Long) { data = session; now = elapsedMs; applyData() }
    private fun applyData() {
        val ready = map ?: return
        val style = ready.style?.takeIf { it.isFullyLoaded } ?: return
        val fresh = data.currentFix(now) != null
        if (lastApplied != data || lastFresh != fresh) {
            style.getSourceAs<GeoJsonSource>(TripMapStyle.ROUTE)?.setGeoJson(TripMapStyle.route(data))
            style.getSourceAs<GeoJsonSource>(TripMapStyle.FIX)?.setGeoJson(TripMapStyle.fix(data))
            style.getLayerAs<CircleLayer>(TripMapStyle.FIX)?.setProperties(circleColor(if (fresh) paint.current else paint.stale))
            if (following && (lastApplied == null || fresh && lastApplied?.lastFix != data.lastFix)) center()
            lastApplied = data; lastFresh = fresh
        }
    }
    fun center() {
        if (destroyed) return
        data.lastFix?.value?.let { map?.moveCamera(CameraUpdateFactory.newLatLng(LatLng(it.latitude, it.longitude))) }
        following = true
    }
    fun fitTrace() {
        val points = data.segments.flatten()
        if (destroyed || points.size < 2) return
        following = false
        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
        map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, Glass.mapFitPadding))
    }
    fun visible(value: Boolean) { visible = value; syncLifecycle() }
    fun lifecycle(state: Lifecycle.State) { lifecycle = state; syncLifecycle() }
    private fun syncLifecycle() {
        if (destroyed) return
        val wantStarted = visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val wantResumed = wantStarted && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        TripMapNetwork.visible(token, online && wantResumed)
        if (resumed && !wantResumed) { view.onPause(); resumed = false }
        if (started && !wantStarted) { view.onStop(); started = false }
        if (!started && wantStarted) { view.onStart(); started = true }
        if (!resumed && wantResumed) { view.onResume(); resumed = true }
    }
    fun destroy() {
        if (destroyed) return
        visible(false)
        destroyed = true
        view.onDestroy(); map = null
        TripMapNetwork.visible(token, false)
    }
}

@Composable
private fun NativeTripMap(session: TripMapSession, now: Long, online: Boolean) {
    NativeTripMapInternal(session, now, online, Modifier, controls = true)
}

@Composable
fun FullScreenTripMap(session: TripMapSession, now: Long, online: Boolean, modifier: Modifier = Modifier) {
    NativeTripMapInternal(session, now, online, modifier, controls = false)
}

@Composable
private fun NativeTripMapInternal(
    session: TripMapSession,
    now: Long,
    online: Boolean,
    modifier: Modifier,
    controls: Boolean
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme
    fun Color.css() = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)
    val paint = TripMapPaint(colors.background.css(), colors.primary.css(), colors.tertiary.css(),
        colors.onSurfaceVariant.css(), colors.surface.css(), Glass.mapRouteWidth, Glass.mapPointRadius, Glass.mapPointRimWidth,
        if (com.gust4av033.appcopilotomoto.presentation.theme.LocalGlassDark.current) Glass.mapNightBrightness else 1f)
    var retry by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(R.string.trip_map_initializing) }
    key(online, retry, paint) {
        val creation = remember { runCatching { NativeTripMapController(context, session, online, paint) { status = it } } }
        val controller = creation.getOrNull()
        if (!controls) {
            Box(modifier) {
                if (controller != null) {
                    AndroidView(factory = { controller.view }, update = { controller.update(session, now) },
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                controller.visible(bounds.width > 0 && bounds.height > 0)
                            })
                    Surface(color = colors.surface, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = Glass.iconButtonSize)
                        .clickable { runCatching { uri.openUri(if (online) "https://openfreemap.org/" else "https://maplibre.org/") }
                            .onFailure { status = R.string.trip_map_link_error } }) {
                        Text(stringResource(if (online) R.string.trip_map_overlay_attribution else R.string.trip_map_engine),
                            Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    DisposableEffect(controller, lifecycle) {
                        val observer = LifecycleEventObserver { _, _ -> controller.lifecycle(lifecycle.currentState) }
                        lifecycle.addObserver(observer); controller.lifecycle(lifecycle.currentState)
                        onDispose { lifecycle.removeObserver(observer); controller.destroy() }
                    }
                } else {
                    Text(stringResource(R.string.trip_map_render_error),
                        modifier = Modifier.align(Alignment.Center),
                        color = colors.error)
                }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (controller != null) {
                Box(Modifier.fillMaxWidth().height(Glass.mapHeight).clip(MaterialTheme.shapes.medium)) {
                AndroidView(factory = { controller.view }, update = { controller.update(session, now) },
                    modifier = Modifier.fillMaxWidth().height(Glass.mapHeight)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            controller.visible(bounds.width > 0 && bounds.height > 0)
                        })
                Surface(color = colors.surface, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(min = Glass.iconButtonSize)
                    .clickable { runCatching { uri.openUri(if (online) "https://openfreemap.org/" else "https://maplibre.org/") }
                        .onFailure { status = R.string.trip_map_link_error } }) {
                    Text(stringResource(if (online) R.string.trip_map_overlay_attribution else R.string.trip_map_engine),
                        Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
                }
                DisposableEffect(controller, lifecycle) {
                    val observer = LifecycleEventObserver { _, _ -> controller.lifecycle(lifecycle.currentState) }
                    lifecycle.addObserver(observer); controller.lifecycle(lifecycle.currentState)
                    onDispose { lifecycle.removeObserver(observer); controller.destroy() }
                }
                SupportingText(stringResource(status))
                ActionButton(stringResource(R.string.trip_map_center)) { controller.center() }
                if (session.segments.any { it.size >= 2 }) ActionButton(stringResource(R.string.trip_map_fit)) { controller.fitTrace() }
            } else Text(stringResource(R.string.trip_map_render_error), color = colors.error)
            if (controller == null || status in setOf(R.string.trip_map_load_error, R.string.trip_map_render_error, R.string.trip_map_tile_error)) {
                ActionButton(stringResource(R.string.trip_map_retry)) { status = R.string.trip_map_initializing; retry++ }
            }
        }
    }
}

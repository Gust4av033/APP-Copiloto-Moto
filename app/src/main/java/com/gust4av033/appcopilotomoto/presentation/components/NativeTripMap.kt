package com.gust4av033.appcopilotomoto.presentation.components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.Property
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.cos
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
val LocalTripMapRiding = staticCompositionLocalOf { false }
val LocalTripMapTopInset = staticCompositionLocalOf<Dp> { 0.dp }
val LocalTripMapBottomInset = staticCompositionLocalOf<Dp> { 0.dp }
val LocalTripMapRenderer = staticCompositionLocalOf<TripMapRenderer> { TripMapRenderer { session, now, online ->
    NativeTripMap(session, now, online)
} }

internal class NativeTripMapController(context: Context, first: TripMapSession, private val online: Boolean,
    private val paint: TripMapPaint, private var inclined: Boolean,
    private var cameraMode: TripMapCameraMode = TripMapCameraMode.FOLLOW_HEADING,
    private val onCameraModeChanged: (TripMapCameraMode) -> Unit = {}, private val report: (Int) -> Unit) {
    private val token = Any()
    private var destroyed = false
    private var visible = false
    private var lifecycle = Lifecycle.State.CREATED
    private var started = false
    private var resumed = false
    private var modelKey: List<Double>? = null
    private var map: MapLibreMap? = null
    private var vegetation: NativeVegetation? = null
    private var guidance = TripMapStyle.emptyCollection().toString()
    private var data = first
    private var now = 0L
    private var lastApplied: TripMapSession? = null
    private var lastFresh: Boolean? = null
    private val marker = ImageView(context).apply {
        setImageResource(R.drawable.ic_ignitor_vector)
        id = R.id.trip_map_motorcycle_marker
        visibility = View.INVISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }
    val view: MapView
    init {
        MapLibre.getInstance(context.applicationContext, null, WellKnownTileServer.MapLibre, RenderingEngine.Type.OPENGL)
        HttpRequestUtil.setOkHttpClient(TripMapNetwork.client(context))
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        val fix = first.lastFix?.value
        val camera = if (fix == null) worldCamera() else CameraPosition.Builder()
            .target(LatLng(fix.latitude, fix.longitude)).zoom(Glass.rideStreetZoom).tilt(pitch()).build()
        view = MapView(context, MapLibreMapOptions().textureMode(true).actionJournalEnabled(false)
            .camera(camera)
            .setPrefetchesTiles(false).setPrefetchZoomDelta(0).minZoomPreference(0.0).maxZoomPreference(maxZoom(first))
            .tiltGesturesEnabled(false).rotateGesturesEnabled(false).compassEnabled(false)
            .logoEnabled(false).attributionEnabled(false)) // Compose draws the persistent linked attribution over the map.
        view.onCreate(null)
        val markerSize = (Glass.rideMarkerSize.value * context.resources.displayMetrics.density).roundToInt()
        view.addView(marker, FrameLayout.LayoutParams(markerSize, markerSize, Gravity.TOP or Gravity.LEFT))
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionMarker() }
        view.setMaximumFps(30)
        view.addOnDidFailLoadingMapListener { if (!destroyed) report(R.string.trip_map_load_error) }
        view.addOnRenderErrorListener { if (!destroyed) report(R.string.trip_map_render_error) }
        view.addOnTileActionListener { operation, _, _, _, _, _, source ->
            if (!destroyed && online && source == TripMapStyle.VECTOR) when (operation) {
                TileOperation.Error -> report(R.string.trip_map_tile_error)
                TileOperation.LoadFromCache, TileOperation.LoadFromNetwork -> report(R.string.trip_map_tiles_ready)
                else -> Unit
            }
        }
        view.getMapAsync { ready ->
            if (destroyed) return@getMapAsync
            map = ready
            vegetation = NativeVegetation(ready, { view.width }, { view.height }, paint.brightness < 1f)
            ready.setPrefetchesTiles(false); ready.setPrefetchZoomDelta(0)
            ready.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) setCameraMode(TripMapCameraMode.FREE)
            }
            ready.addOnCameraMoveListener { positionMarker() }
            ready.addOnCameraIdleListener { updateModel(); vegetation?.request() }
            view.addOnDidFinishRenderingMapListener { vegetation?.request() }
            ready.uiSettings.isRotateGesturesEnabled = cameraMode == TripMapCameraMode.FREE
            val base = if (online) context.assets.open("map/liberty.json")
                .bufferedReader().use { it.readText() } else null
            val direction = android.graphics.Bitmap.createBitmap(28, 20, android.graphics.Bitmap.Config.ARGB_8888)
            context.getDrawable(R.drawable.ic_road_direction)?.apply {
                setBounds(0, 0, 28, 20); draw(android.graphics.Canvas(direction))
            }
            val style = Style.Builder().fromJson(TripMapStyle.json(online, paint, base))
                .withImage("app-road-direction", direction)
            ready.setStyle(style) {
                if (!destroyed) {
                    lastApplied = null; modelKey = null;
                    ready.style?.getSourceAs<GeoJsonSource>(TripMapStyle.GUIDANCE)?.setGeoJson(guidance)
                    applyData(); updateModel(); vegetation?.visible(online && inclined && resumed)
                    report(if (online) R.string.trip_map_loading_tiles else R.string.trip_map_local_only)
                }
            }
        }
    }
    // Separate from GPS history; survives style load, empty until a routing producer calls it.
    fun showRoute(points: List<MapRoutePoint>) {
        guidance = TripMapStyle.plannedRoute(points)
        map?.style?.takeIf { it.isFullyLoaded }?.getSourceAs<GeoJsonSource>(TripMapStyle.GUIDANCE)?.setGeoJson(guidance)
    }
    fun clearRoute() = showRoute(emptyList())
    fun update(session: TripMapSession, elapsedMs: Long) { data = session; now = elapsedMs; applyData() }
    private fun applyData() {
        val ready = map ?: return
        val style = ready.style?.takeIf { it.isFullyLoaded } ?: return
        val fresh = data.currentFix(now) != null
        if (lastApplied != data || lastFresh != fresh) {
            if (ready.maxZoomLevel != maxZoom(data)) ready.setMaxZoomPreference(maxZoom(data))
            style.getSourceAs<GeoJsonSource>(TripMapStyle.ROUTE)?.setGeoJson(TripMapStyle.route(data))
            style.getSourceAs<GeoJsonSource>(TripMapStyle.FIX)?.setGeoJson(TripMapStyle.fix(data))
            style.getLayerAs<CircleLayer>(TripMapStyle.FIX)?.setProperties(circleColor(if (fresh) paint.current else paint.stale))
            marker.alpha = if (fresh) 1f else .74f
            if (data.lastFix == null && lastApplied?.lastFix != null) {
                ready.moveCamera(CameraUpdateFactory.newCameraPosition(worldCamera()))
                setCameraMode(TripMapCameraMode.FOLLOW_HEADING)
            } else if (cameraMode != TripMapCameraMode.FREE && (lastApplied?.lastFix != data.lastFix || lastFresh != fresh)) followPosition()
            positionMarker()
            updateModel()
            lastApplied = data; lastFresh = fresh
        }
    }
    // Pixel coordinates from MapLibre already include pan, zoom and pitch.
    // Updating a native child avoids a Compose pass for every gesture frame.
    private fun positionMarker() {
        val ready = map ?: return
        val fix = data.lastFix?.value
        if (destroyed || fix == null || marker.width == 0 || inclined && ready.cameraPosition.zoom >= 14) {
            marker.visibility = View.INVISIBLE
            return
        }
        val point = ready.projection.toScreenLocation(LatLng(fix.latitude, fix.longitude))
        marker.translationX = point.x - marker.width / 2f
        marker.translationY = point.y - marker.height / 2f
        marker.visibility = if (point.x in 0f..view.width.toFloat() && point.y in 0f..view.height.toFloat())
            View.VISIBLE else View.INVISIBLE
    }

    private fun maxZoom(session: TripMapSession) = if (!online && session.lastFix == null) 6.0 else 19.0

    private fun worldCamera() = CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(0.0).tilt(0.0).build()
    private fun pitch() = if (inclined) Glass.rideMapPitch else 0.0

    fun setPerspective(value: Boolean) {
        if (inclined == value) return
        inclined = value
        val ready = map ?: return
        if (data.lastFix != null || ready.cameraPosition.zoom >= 12) ready.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(ready.cameraPosition).tilt(pitch()).build()))
        updateModel()
        vegetation?.visible(online && inclined && resumed)
        map?.style?.getLayer(TripMapStyle.TREES)?.setProperties(visibility(if (inclined) Property.VISIBLE else Property.NONE))
    }
    fun setCameraMode(mode: TripMapCameraMode) {
        cameraMode = mode
        onCameraModeChanged(mode)
        map?.uiSettings?.isRotateGesturesEnabled = mode == TripMapCameraMode.FREE
        if (mode != TripMapCameraMode.FREE) followPosition()
    }
    fun center() = setCameraMode(TripMapCameraMode.FOLLOW_HEADING)
    private fun followPosition() {
        if (destroyed) return
        val ready = map ?: return
        val fix = data.lastFix?.value
        val position = ready.cameraPosition
        if (fix == null) {
            ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(position).bearing(0.0)
                .tilt(if (position.zoom >= 12) pitch() else 0.0).build()))
            return
        }
        val zoom = if (lastApplied?.lastFix == null || position.zoom < 8) Glass.rideStreetZoom else position.zoom
        val bearing = if (cameraMode == TripMapCameraMode.FOLLOW_HEADING)
            tripMapHeadingDegrees(data.currentFix(now)) ?: 0.0 else 0.0
        ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(position)
            .target(LatLng(fix.latitude, fix.longitude)).bearing(bearing).zoom(zoom).tilt(pitch()).build()))
    }
    private fun updateModel() {
        val ready = map ?: return
        val style = ready.style?.takeIf { it.isFullyLoaded } ?: return
        val wantedPitch = if (ready.cameraPosition.zoom >= 12) pitch() else 0.0
        if (kotlin.math.abs(ready.cameraPosition.tilt - wantedPitch) > .1)
            ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(ready.cameraPosition).tilt(wantedPitch).build()))
        val fix = data.lastFix?.value
        val show = inclined && fix != null && ready.cameraPosition.zoom >= 14
        style.getLayer(TripMapStyle.MOTO)?.setProperties(visibility(if (show) Property.VISIBLE else Property.NONE))
        positionMarker()
        if (!show) return
        // An avatar of readable size at street zoom. Do not regenerate during camera frames.
        val zoom = (ready.cameraPosition.zoom * 4).roundToInt() / 4.0
        val scale = (40075016.686 * cos(Math.toRadians(fix.latitude)) / (512 * 2.0.pow(zoom)) * Glass.modelDisplayLength / 2.1)
            .coerceIn(1.0, 80.0)
        val heading = tripMapHeadingDegrees(data.currentFix(now)) ?: 0.0
        val key = listOf(fix.latitude, fix.longitude, heading, scale)
        if (modelKey != key) {
            modelKey = key
            style.getSourceAs<GeoJsonSource>(TripMapStyle.MOTO)?.setGeoJson(
                MotorcycleMapModel.geoJson(fix.latitude, fix.longitude, heading, scale))
        }
    }
    fun fitTrace() {
        val points = data.segments.flatten()
        if (destroyed || points.size < 2) return
        setCameraMode(TripMapCameraMode.FREE)
        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
        map?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, pitch(), Glass.mapFitPadding))
    }
    fun visible(value: Boolean) { visible = value; syncLifecycle() }
    fun lifecycle(state: Lifecycle.State) { lifecycle = state; syncLifecycle() }
    private fun syncLifecycle() {
        if (destroyed) return
        val wantStarted = visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val wantResumed = wantStarted && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
        TripMapNetwork.visible(token, online && wantResumed)
        vegetation?.visible(online && inclined && wantResumed)
        if (resumed && !wantResumed) { view.onPause(); resumed = false }
        if (started && !wantStarted) { view.onStop(); started = false }
        if (!started && wantStarted) { view.onStart(); started = true }
        if (!resumed && wantResumed) { view.onResume(); resumed = true }
    }
    fun destroy() {
        if (destroyed) return
        visible(false)
        destroyed = true
        vegetation?.destroy(); vegetation = null
        view.onDestroy(); map = null
        TripMapNetwork.visible(token, false)
    }
}

@Composable
private fun NativeTripMap(session: TripMapSession, now: Long, online: Boolean) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme
    fun Color.css() = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)
    val paint = TripMapPaint(colors.background.css(), Glass.mapTraceColor, Glass.motoBlue,
        colors.onSurfaceVariant.css(), colors.surface.css(), Glass.mapRouteWidth, Glass.mapPointRadius, Glass.mapPointRimWidth,
        if (com.gust4av033.appcopilotomoto.presentation.theme.LocalGlassDark.current) Glass.mapNightBrightness else 1f,
        colors.secondaryContainer.css(), colors.outline.css())
    var retry by remember { mutableIntStateOf(0) }
    val riding = LocalTripMapRiding.current
    val bottomInset = LocalTripMapBottomInset.current
    val topInset = LocalTripMapTopInset.current
    var cameraMode by rememberSaveable { mutableStateOf(TripMapCameraMode.NORTH_UP) }
    var attributionOpen by remember { mutableStateOf(false) }
    var inclined by rememberSaveable { mutableStateOf(true) }
    key(online, retry, paint) {
        var status by remember { mutableIntStateOf(R.string.trip_map_initializing) }
        val controller = remember {
            runCatching { NativeTripMapController(context, session, online, paint, inclined, cameraMode, { cameraMode = it }) { status = it } }.getOrNull()
        }
        if (controller == null) {
            MapRecovery(R.string.trip_map_render_error) { retry++ }
        } else {
            DisposableEffect(controller, lifecycle) {
                val observer = LifecycleEventObserver { _, _ -> controller.lifecycle(lifecycle.currentState) }
                lifecycle.addObserver(observer)
                controller.lifecycle(lifecycle.currentState)
                onDispose { lifecycle.removeObserver(observer); controller.destroy() }
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { controller.view }, update = {
                    controller.setPerspective(inclined)
                    controller.update(session, now)
                }, modifier = Modifier.fillMaxSize().onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    controller.visible(bounds.width > 0 && bounds.height > 0)
                })
                if (online || session.lastFix != null) {
                    Column(Modifier.align(Alignment.TopEnd).padding(top = topInset, bottom = bottomInset)
                        .padding(8.dp).width(Glass.cockpitSideWidth).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!riding) {
                        CockpitButton(stringResource(if (inclined) R.string.ride_map_3d else R.string.ride_map_2d), R.drawable.ic_map_layers, Modifier.fillMaxWidth(),
                            selected = inclined, description = stringResource(if (inclined) R.string.map_flat else R.string.map_perspective)) { inclined = !inclined }
                        CockpitButton(stringResource(R.string.map_heading_short), R.drawable.ic_heading, Modifier.fillMaxWidth(),
                            selected = cameraMode == TripMapCameraMode.FOLLOW_HEADING, enabled = session.lastFix != null,
                            description = stringResource(R.string.trip_map_camera_heading)) { controller.setCameraMode(TripMapCameraMode.FOLLOW_HEADING) }
                        CockpitButton(stringResource(R.string.map_north_short), R.drawable.ic_north, Modifier.fillMaxWidth(),
                            selected = cameraMode == TripMapCameraMode.NORTH_UP,
                            description = stringResource(R.string.trip_map_camera_north)) { controller.setCameraMode(TripMapCameraMode.NORTH_UP) }
                        CockpitButton(stringResource(R.string.map_free_short), R.drawable.ic_pan, Modifier.fillMaxWidth(),
                            selected = cameraMode == TripMapCameraMode.FREE,
                            description = stringResource(R.string.trip_map_camera_free)) { controller.setCameraMode(TripMapCameraMode.FREE) }
                        }
                        if (!riding || cameraMode == TripMapCameraMode.FREE) CockpitButton(stringResource(R.string.map_center_short), R.drawable.ic_center_fix, Modifier.fillMaxWidth(),
                            enabled = session.lastFix != null, description = stringResource(R.string.trip_map_center), onClick = controller::center)
                        if (!riding && session.segments.any { it.size >= 2 }) CockpitButton(stringResource(R.string.map_trace_short),
                            R.drawable.ic_trip, Modifier.fillMaxWidth(), description = stringResource(R.string.trip_map_fit), onClick = controller::fitTrace)
                    }
                }
                val link = "https://www.naturalearthdata.com/"
                Surface(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = bottomInset).padding(4.dp)
                    .clickable(role = Role.Button) { if (online) attributionOpen = true else runCatching { uri.openUri(link) }.onFailure { status = R.string.trip_map_link_error } },
                    shape = MaterialTheme.shapes.medium, color = colors.surface) {
                    Box(Modifier.heightIn(min = Glass.iconButtonSize).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (online) R.string.trip_map_overlay_attribution else R.string.map_atlas),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (attributionOpen) AlertDialog(onDismissRequest = { attributionOpen = false },
                    title = { Text(stringResource(R.string.map_sources)) }, text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            listOf("OpenFreeMap" to "https://openfreemap.org/",
                                "© OpenMapTiles" to "https://openmaptiles.org/",
                                "© OpenStreetMap contributors" to "https://www.openstreetmap.org/copyright",
                                "Liberty · CC BY 4.0" to "https://github.com/maputnik/osm-liberty").forEach { (label, url) ->
                                ActionButton(label) { runCatching { uri.openUri(url) }.onFailure { status = R.string.trip_map_link_error } }
                            }
                        }
                    }, confirmButton = { TextButton(onClick = { attributionOpen = false }) { Text(stringResource(R.string.ride_done)) } })
                if (status == R.string.trip_map_load_error || status == R.string.trip_map_render_error ||
                    status == R.string.trip_map_tile_error || status == R.string.trip_map_link_error) {
                    Surface(modifier = Modifier.align(Alignment.Center).padding(12.dp), shape = MaterialTheme.shapes.medium) {
                        MapRecovery(status) { retry++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapRecovery(message: Int, onRetry: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        SupportingText(stringResource(message))
        ActionButton(stringResource(R.string.trip_map_retry), onClick = onRetry)
    }
}

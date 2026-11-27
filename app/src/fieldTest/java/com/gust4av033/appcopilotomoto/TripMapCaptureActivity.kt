package com.gust4av033.appcopilotomoto

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import com.gust4av033.appcopilotomoto.presentation.theme.LocalGlassDark
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotBackdrop
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme

/** Private field-test scenario. Never enters TripRecordingState, the GPS, voice, or a repository. */
class TripMapCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = SystemClock.elapsedRealtime()
        val points = listOf(TripTrackPoint(14.6004, -90.5, now - 2000),
            TripTrackPoint(14.6005, -90.4999, now - 1000), TripTrackPoint(14.6006, -90.5, now))
        val fix = LocationSample(0, now, 14.6006, -90.5, null, null, null, null, null, 3f, "native-render-fixture", true)
        val session = TripMapSession("native-render-fixture", now - 2000, lastFix =
            ContextValue(fix, ContextSource.REPLAY_FIXTURE, 0, now, 1f, 10_000, true),
            distanceMeters = 80.0, segments = listOf(points))
        setContent {
            CopilotTheme { CopilotBackdrop {
                val routeColor = android.graphics.Color.parseColor(Glass.mapTraceColor)
                SideEffect { intent.putExtra("fixture_route_color", routeColor) }
                Column(Modifier.fillMaxSize()) {
                    Text("PRUEBA NATIVA · ESCENA Y CLIMA SINTÉTICOS", Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall)
                    val epoch = System.currentTimeMillis()
                    CopilotApp(CopilotUiState(tripActive = true, tripMap = session, observedRouteAtElapsedMs = now,
                        tripLocationEnabled = true, tripLocationAvailable = true,
                        weather = WeatherUiState(enabled = true, hasTarget = true, nowElapsedMs = now, nowEpochMs = epoch,
                            data = ContextValue(WeatherObservation(24.0, "native-render-fixture", 2, 8.0, epoch, "America/El_Salvador"),
                                ContextSource.REPLAY_FIXTURE, epoch, now, 1f, 60_000, true))),
                        CopilotScreen.MAP, {}, CopilotActions())
                }
            } }
        }
    }
}

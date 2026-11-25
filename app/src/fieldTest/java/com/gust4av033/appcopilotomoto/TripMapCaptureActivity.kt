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
import com.gust4av033.appcopilotomoto.presentation.components.LocalTripMapRenderer
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotBackdrop
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme

/** Private field-test scenario. Never enters TripRecordingState, the GPS, voice, or a repository. */
class TripMapCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = SystemClock.elapsedRealtime()
        val points = listOf(TripTrackPoint(14.6, -90.5, now - 2000),
            TripTrackPoint(14.6003, -90.4998, now - 1000), TripTrackPoint(14.6006, -90.5, now))
        val fix = LocationSample(0, now, 14.6006, -90.5, null, null, null, null, null, 3f, "native-render-fixture", true)
        val session = TripMapSession("native-render-fixture", now - 2000, lastFix =
            ContextValue(fix, ContextSource.REPLAY_FIXTURE, 0, now, 1f, 10_000, true),
            distanceMeters = 80.0, segments = listOf(points))
        setContent {
            CopilotTheme { CopilotBackdrop {
                val routeColor = MaterialTheme.colorScheme.primary.toArgb()
                SideEffect { intent.putExtra("fixture_route_color", routeColor) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PRUEBA NATIVA · DATOS SINTÉTICOS")
                    Text("MapLibre real, sin calles ni solicitudes de red. GPS y micrófono apagados.")
                    Box(Modifier.fillMaxWidth().height(320.dp)) { LocalTripMapRenderer.current.Render(session, now, false) }
                }
            } }
        }
    }
}

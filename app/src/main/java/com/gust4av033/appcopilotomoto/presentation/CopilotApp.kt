package com.gust4av033.appcopilotomoto.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.screens.SettingsScreen
import com.gust4av033.appcopilotomoto.presentation.screens.TestsScreen
import com.gust4av033.appcopilotomoto.presentation.screens.TripScreen
import com.gust4av033.appcopilotomoto.presentation.theme.CopilotTheme

@Composable
fun CopilotApp(
    state: CopilotUiState,
    selectedScreen: CopilotScreen,
    onScreenChange: (CopilotScreen) -> Unit,
    actions: CopilotActions
) {
    // Volver desde Ajustes o Pruebas lleva a Viaje. La navegación no toca el micrófono.
    if (!LocalInspectionMode.current) {
        BackHandler(enabled = selectedScreen != CopilotScreen.TRIP) { onScreenChange(CopilotScreen.TRIP) }
    }
    CopilotTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    CopilotScreen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = selectedScreen == screen,
                            onClick = { onScreenChange(screen) },
                            icon = { Icon(painterResource(screen.iconRes), contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = { Text(stringResource(screen.titleRes)) }
                        )
                    }
                }
            }
        ) { padding ->
            val screenState = rememberSaveableStateHolder()
            screenState.SaveableStateProvider(selectedScreen.name) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.ui_brand), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(selectedScreen.titleRes), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ui_phase), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(4.dp))
                        when (selectedScreen) {
                            CopilotScreen.TRIP -> TripScreen(state, actions)
                            CopilotScreen.TESTS -> TestsScreen(state, actions)
                            CopilotScreen.SETTINGS -> SettingsScreen(state, actions)
                        }
                    }
                }
            }
        }
    }
}

// Datos exclusivamente de preview: nunca se invocan servicios o proveedores de audio.
private val PreviewState = CopilotUiState(
    tripStatus = "Iniciá la sesión con el casco conectado.",
    lastCommandResult = "Todavía no se ha enviado una orden.",
    audioDiagnostic = "Actualizá los niveles antes y después de iniciar el viaje.",
    voiceMessage = "Listo para escuchar una orden corta.",
    probeMessage = "La prueba dura cinco segundos.",
    wakeMessage = "Detector detenido.",
    assistantMessage = "Elegí APP como asistente para probar el botón del casco.",
    callMessage = "Las llamadas necesitan permisos de teléfono y contactos."
)

@Preview(name = "Viaje día", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Viaje noche", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Viaje texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun TripPreview() {
    CopilotApp(PreviewState, CopilotScreen.TRIP, {}, CopilotActions())
}

@Preview(name = "Viaje activo", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ActiveTripPreview() {
    CopilotApp(
        PreviewState.copy(tripActive = true, tripStatus = "Esperando Alexa.", metrics = TripMetricsUi(4, 1, 5, 5, 0)),
        CopilotScreen.TRIP, {}, CopilotActions()
    )
}

@Preview(name = "Pruebas", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun TestsPreview() {
    CopilotApp(PreviewState, CopilotScreen.TESTS, {}, CopilotActions())
}

@Preview(name = "Ajustes", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Ajustes texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun SettingsPreview() {
    CopilotApp(PreviewState, CopilotScreen.SETTINGS, {}, CopilotActions())
}

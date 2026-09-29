package com.gust4av033.appcopilotomoto.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import com.gust4av033.appcopilotomoto.presentation.screens.HomeScreen
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
    // Las rutas secundarias conservan su origen; navegar no toca el runtime.
    var testsParentName by rememberSaveable { mutableStateOf(CopilotScreen.SETTINGS.name) }
    val testsParent = runCatching { CopilotScreen.valueOf(testsParentName) }.getOrDefault(CopilotScreen.SETTINGS)
    val navigate: (CopilotScreen) -> Unit = { screen ->
        if (screen == CopilotScreen.TESTS && selectedScreen != CopilotScreen.TESTS) {
            testsParentName = selectedScreen.name
        }
        onScreenChange(screen)
    }
    val navigateBack: () -> Unit = {
        navigate(if (selectedScreen == CopilotScreen.TESTS) testsParent else CopilotScreen.HOME)
    }
    val mainSelection = if (selectedScreen == CopilotScreen.TESTS) testsParent else selectedScreen
    if (!LocalInspectionMode.current) {
        BackHandler(enabled = selectedScreen != CopilotScreen.HOME, onBack = navigateBack)
    }
    CopilotTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (state.tripActive && selectedScreen != CopilotScreen.TRIP) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.trip_active), modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge)
                                TextButton(onClick = { navigate(CopilotScreen.TRIP) }) {
                                    Text(stringResource(R.string.home_open_trip))
                                }
                            }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        CopilotScreen.entries.filter { it.inMainNavigation }.forEach { screen ->
                            NavigationBarItem(
                                selected = mainSelection == screen,
                                onClick = { navigate(screen) },
                                icon = { Icon(painterResource(screen.iconRes), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                label = { Text(stringResource(screen.titleRes)) }
                            )
                        }
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
                        if (selectedScreen == CopilotScreen.TESTS) {
                            TextButton(onClick = navigateBack) {
                                Text(stringResource(R.string.back_to_screen, stringResource(testsParent.titleRes)))
                            }
                        }
                        Text(stringResource(R.string.ui_brand), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(selectedScreen.titleRes), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.ui_phase), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(4.dp))
                        when (selectedScreen) {
                            CopilotScreen.HOME -> HomeScreen(state, actions, navigate)
                            CopilotScreen.TRIP -> TripScreen(state, actions) { navigate(CopilotScreen.TESTS) }
                            CopilotScreen.TESTS -> TestsScreen(state, actions)
                            CopilotScreen.SETTINGS -> SettingsScreen(state, actions) { navigate(CopilotScreen.TESTS) }
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
    audioBefore = "Modo normal · música 10/15",
    audioDuring = "Modo comunicación · comunicación 8/15",
    audioAfter = "Modo normal · música 10/15",
    voiceMessage = "Listo para escuchar una orden corta.",
    probeMessage = "La prueba dura cinco segundos.",
    wakeMessage = "Detector detenido.",
    assistantMessage = "Elegí APP como asistente para probar el botón del casco.",
    callMessage = "Las llamadas necesitan permisos de teléfono y contactos."
)

@Preview(name = "Inicio día", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Preview(name = "Inicio noche", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Inicio texto grande", showBackground = true, widthDp = 360, heightDp = 800, locale = "es", fontScale = 1.5f)
@Composable
private fun HomePreview() {
    CopilotApp(PreviewState, CopilotScreen.HOME, {}, CopilotActions())
}

@Preview(name = "Inicio sesión activa", showBackground = true, widthDp = 360, heightDp = 800, locale = "es")
@Composable
private fun ActiveHomePreview() {
    CopilotApp(PreviewState.copy(tripActive = true, tripStatus = "Esperando Alexa."), CopilotScreen.HOME, {}, CopilotActions())
}

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

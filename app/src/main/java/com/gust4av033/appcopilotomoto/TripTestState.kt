package com.gust4av033.appcopilotomoto

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TripCallPhase { NONE, CALL, RECOVERING }

/** Estado de diagnóstico; las escrituras se hacen en el hilo principal. */
object TripTestState {
    var pipeline by mutableStateOf(TripPipelineMetrics())
    fun record(context: Context, event: PipelineEvent) { pipeline = pipeline.record(event); save(context) }
    var running by mutableStateOf(false)
    var restoringAudio by mutableStateOf(false)
    var pendingAppName by mutableStateOf<String?>(null)
    var pendingDraftText by mutableStateOf<String?>(null)
    var pendingAppToken by mutableStateOf<String?>(null)
    internal var pendingAppTarget: AppTarget? = null
    internal var pendingAppExpiresAtMs = 0L
    internal var openPendingAppFromVoice: ((String) -> Boolean)? = null
    internal var playMusicFromVoice: ((MusicSearchRequest) -> Result<Unit>)? = null
    var commandReady by mutableStateOf(false)
    var audioMode by mutableStateOf(WakeAudioMode.BLUETOOTH)
    var manualActivationOnly by mutableStateOf(false)
    var switchingMode by mutableStateOf(false)
    var interactionPhase by mutableStateOf(TripInteractionPhase.PREPARING)
    var appVisible = false
    var overlayHidden by mutableStateOf(false)
    var overlayMessage by mutableStateOf("")
    var callPhase by mutableStateOf(TripCallPhase.NONE)
    var status by mutableStateOf("Iniciá la sesión de prueba con la app abierta.")
    var lastCommandResult by mutableStateOf("Aún no hay órdenes en esta sesión.")
    var lastRoutedCommand by mutableStateOf<RoutedCommand?>(null)
    var detections by mutableStateOf(0)
    var manualCommands by mutableStateOf(0)
    var recognized by mutableStateOf(0)
    var actions by mutableStateOf(0)
    var failures by mutableStateOf(0)
    var lastWakeToSttMs by mutableStateOf(0L)
    var lastLatencyMs by mutableStateOf(0L)
    var audioBefore by mutableStateOf("Sin lectura previa.")
    var audioDuring by mutableStateOf("Iniciá una sesión para medir la ruta activa.")
    var audioAfter by mutableStateOf("Detené la sesión para medir la ruta restaurada.")
    var audioCaptureGeneration = 0L

    private const val PREFS = "fase_0_3_metricas"

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        pipeline = TripPipelineMetrics(counts = PipelineEvent.entries.associateWith { prefs.getInt("pipeline_${it.name}", 0) },
            lastSttMs = prefs.getLong("pipeline_stt_ms", 0L),
            lastCaptureContext = prefs.getString("pipeline_context", "Sin captura.").orEmpty(),
            lastEndReason = prefs.getString("pipeline_end", "Sin cierre inesperado.").orEmpty(),
            lastButtonStatus = prefs.getString("pipeline_button", TripPipelineMetrics.DEFAULT_BUTTON_STATUS).orEmpty().let {
                if (it == "Sin botón dedicado." || it.isBlank()) TripPipelineMetrics.DEFAULT_BUTTON_STATUS else it
            },
            lastButtonEvent = prefs.getString("pipeline_button_event", "Sin pulsación recibida.").orEmpty(),
            lastReadyCue = prefs.getString("pipeline_ready_cue", "Sin señal para hablar.").orEmpty())
        if (prefs.getBoolean("session_active", false) && !running) {
            pipeline = pipeline.record(PipelineEvent.SESSION_UNEXPECTED_END).copy(lastEndReason = "Proceso interrumpido con viaje activo")
            status = "La sesión anterior se interrumpió; iniciá una nueva prueba."
            prefs.edit().putBoolean("session_active", false).apply()
        }
        detections = prefs.getInt("detections", 0)
        manualCommands = prefs.getInt("manual_commands", 0)
        recognized = prefs.getInt("recognized", 0)
        actions = prefs.getInt("actions", 0)
        failures = prefs.getInt("failures", 0)
        lastWakeToSttMs = prefs.getLong("ready_latency", 0L)
        lastLatencyMs = prefs.getLong("latency", 0L)
        save(context)
    }

    fun reset(context: Context) {
        pipeline = TripPipelineMetrics()
        lastCommandResult = "Aún no hay órdenes en esta sesión."
        lastRoutedCommand = null
        detections = 0
        manualCommands = 0
        recognized = 0
        actions = 0
        failures = 0
        lastWakeToSttMs = 0L
        lastLatencyMs = 0L
        save(context)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            PipelineEvent.entries.forEach { putInt("pipeline_${it.name}", pipeline.count(it)) }
            putLong("pipeline_stt_ms", pipeline.lastSttMs)
            putString("pipeline_context", pipeline.lastCaptureContext)
            putString("pipeline_end", pipeline.lastEndReason)
            putString("pipeline_button", pipeline.lastButtonStatus)
            putString("pipeline_button_event", pipeline.lastButtonEvent)
            putString("pipeline_ready_cue", pipeline.lastReadyCue)
        }.apply()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("detections", detections)
            .putInt("manual_commands", manualCommands)
            .putInt("recognized", recognized)
            .putInt("actions", actions)
            .putInt("failures", failures)
            .putLong("ready_latency", lastWakeToSttMs)
            .putLong("latency", lastLatencyMs)
            .apply()
    }

    fun markRunning(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("session_active", active).apply()
    }
}

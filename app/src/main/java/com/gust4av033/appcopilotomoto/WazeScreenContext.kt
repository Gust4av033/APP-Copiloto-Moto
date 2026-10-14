package com.gust4av033.appcopilotomoto

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors

data class ScreenText(val text: String, val top: Int, val bottom: Int)
data class WazeTurn(val instruction: String, val distance: String? = null)

/** Explicit maneuver text with a nearby distance, never ETA or total route distance. */
object WazeTurnParser {
    fun parse(texts: List<ScreenText>): WazeTurn? {
        val maneuvers = texts.filter {
            val text = MinimalIntentRouter.normalize(it.text)
            Regex("^(?:gira|gire|girar|giro|dobla|doble|doblar|turn|mantente|mantengase|keep|toma|tome|take|continua|continue|sigue|siga|haz|make)\\b").containsMatchIn(text) &&
                Regex("\\b(?:izquierda|derecha|recto|left|right|straight|retorno|rotonda|roundabout|salida|exit)\\b").containsMatchIn(text)
        }.distinctBy { MinimalIntentRouter.normalize(it.text) }
        if (maneuvers.size != 1) return null
        val maneuver = maneuvers.single()
        val distances = texts.filter { kotlin.math.abs(it.top - maneuver.top) <= 180 }.mapNotNull {
            Regex("^(?:en |in )?([0-9]+(?:[.,][0-9]+)?\\s*(?:m|km|metros|kilometros|ft|feet|mi))$")
                .matchEntire(java.text.Normalizer.normalize(it.text.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "").trim())?.groupValues?.get(1)
        }.distinct()
        return WazeTurn(maneuver.text.take(180), distances.singleOrNull())
    }
}

data class WazeScreenSnapshot(val sessionId: String? = null, val connected: Boolean = false,
    val visible: Boolean = false, val observedAtMs: Long = 0, val texts: List<ScreenText> = emptyList(), val turn: WazeTurn? = null) {
    fun recent(now: Long) = observedAtMs > 0 && now >= observedAtMs && now - observedAtMs <= 15_000
    fun answer(now: Long): String = if (!visible || !recent(now) || turn == null)
        "No tengo una indicación reciente de Waze. Dejalo al frente con la ruta iniciada y comprobá el permiso de lectura de pantalla."
        else "Según la pantalla de Waze: ${turn.instruction}${turn.distance?.let { ". Distancia indicada: $it" }.orEmpty()}."
    fun report() = buildString {
        appendLine("Fuente: pantalla de com.waze, experimental. No confirma ruta ni ETA.")
        appendLine("Conectado=$connected; visible=$visible; observadoElapsed=$observedAtMs; sesión=${sessionId != null}")
        if (texts.isEmpty()) appendLine("Sin textos accesibles de Waze en la muestra actual.")
        texts.forEach { appendLine("y=${it.top}..${it.bottom}: ${it.text}") }
        appendLine("Interpretación: $turn")
    }
}

internal object WazeScreenAccess {
    fun enabled(context: Context) = context.getSharedPreferences("waze_screen", Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("waze_screen", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        WazeScreenState.clear()
    }
    fun granted(context: Context) = Settings.Secure.getString(context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':').any {
            ComponentName.unflattenFromString(it) == ComponentName(context, WazeScreenService::class.java)
        }
    fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun allowed(context: Context) = BuildConfig.DEBUG && UserSettingsStore(context).developerEnabled(BuildConfig.DEBUG) &&
        enabled(context) && granted(context) && TripNavigationState.context.sessionId != null &&
        !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    fun answer(context: Context) = if (allowed(context)) WazeScreenState.snapshot.answer(SystemClock.elapsedRealtime())
        else "La lectura experimental de pantalla de Waze no está habilitada para este viaje. Revisá Ajustes, Navegación."
}

object WazeScreenState {
    var snapshot by mutableStateOf(WazeScreenSnapshot())
        private set
    fun session(id: String?) { snapshot = WazeScreenSnapshot(sessionId = id, connected = snapshot.connected) }
    fun clear() { snapshot = snapshot.copy(visible = false, observedAtMs = 0, texts = emptyList(), turn = null) }
    fun connection(connected: Boolean) { clear(); snapshot = snapshot.copy(connected = connected) }
    fun update(session: String?, texts: List<ScreenText>?, now: Long) {
        if (session == null || session != snapshot.sessionId) return
        if (texts == null) { snapshot = snapshot.copy(visible = false); return }
        val bounded = texts.take(64).map { it.copy(text = it.text.take(180)) }
        snapshot = snapshot.copy(visible = true, observedAtMs = now, texts = bounded, turn = WazeTurnParser.parse(bounded))
    }
}

/** Passive, bounded worker reads. No gestures, actions, screenshots, or foreground changes. */
class WazeScreenService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var reading = false
    private val tick = object : Runnable {
        override fun run() { readScreen(); if (!closed) handler.postDelayed(this, 2000) }
    }
    override fun onServiceConnected() { WazeScreenState.connection(true); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    private fun readScreen() {
        if (reading || closed) return
        if (!WazeScreenAccess.allowed(this) || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            WazeScreenState.clear(); return
        }
        val session = TripNavigationState.context.sessionId
        reading = true
        worker.execute {
            val texts = runCatching {
                val root = rootInActiveWindow ?: return@runCatching null
                try {
                    if (root.packageName?.toString() != "com.waze") return@runCatching null
                    val bounds = Rect(); root.getBoundsInScreen(bounds)
                    val rows = arrayListOf<ScreenText>()
                    var visited = 0
                    fun walk(node: AccessibilityNodeInfo, depth: Int) {
                        if (++visited > 200 || depth > 12 || rows.size >= 64) return
                        if (node.packageName?.toString() != "com.waze") return
                        val rect = Rect(); node.getBoundsInScreen(rect)
                        if (node.isVisibleToUser && rect.top < bounds.top + bounds.height() * 0.4) {
                            for (value in listOf(node.text, node.contentDescription)) {
                                val text = value?.toString()?.trim()?.take(180)
                                if (!text.isNullOrEmpty()) rows.add(ScreenText(text, rect.top, rect.bottom))
                            }
                        }
                        for (index in 0 until node.childCount.coerceAtMost(50)) {
                            if (visited >= 200 || rows.size >= 64) break
                            node.getChild(index)?.let { child -> try { walk(child, depth + 1) } finally { child.recycle() } }
                        }
                    }
                    walk(root, 0)
                    rows.distinct().take(64)
                } finally { root.recycle() }
            }.getOrNull()
            val observed = SystemClock.elapsedRealtime()
            handler.post {
                reading = false
                if (closed) return@post
                if (WazeScreenAccess.allowed(this)) WazeScreenState.update(session, texts, observed)
                else WazeScreenState.clear()
            }
        }
    }
    override fun onInterrupt() { WazeScreenState.clear() }
    override fun onDestroy() { closed = true; handler.removeCallbacks(tick); worker.shutdownNow(); WazeScreenState.connection(false); super.onDestroy() }
}

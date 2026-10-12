package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.net.Uri

data class NavigationCapabilities(
    val opensSavedDestinations: Boolean,
    val readsRecentDestinations: Boolean = false,
    val readsRouteContext: Boolean = false,
    val stopsNavigation: Boolean = false
)

/** Contrato inicial: preparar una solicitud no confirma que el navegador inició una ruta. */
interface NavigationProvider {
    val id: String
    val label: String
    val capabilities: NavigationCapabilities
    fun available(): Boolean
    fun prepare(destination: NavigationDestination): Result<AppTarget>
}

class WazeNavigationProvider(private val context: Context) : NavigationProvider {
    override val id = "waze"
    override val label = "Waze"
    override val capabilities = NavigationCapabilities(opensSavedDestinations = true)

    override fun available(): Boolean = runCatching {
        intent(NavigationDestination.HOME).resolveActivity(context.packageManager) != null
    }.getOrDefault(false)

    override fun prepare(destination: NavigationDestination): Result<AppTarget> = runCatching {
        val launch = intent(destination)
        check(launch.resolveActivity(context.packageManager) != null) {
            "Waze no está disponible para abrir destinos. Instalalo y guardá Casa y Trabajo allí."
        }
        AppTarget("Ruta a ${destination.label} en $label", launch,
            navigationDestination = destination)
    }

    private fun intent(destination: NavigationDestination) =
        Intent(Intent.ACTION_VIEW, Uri.parse(WazeNavigationLink.forDestination(destination)))
            .setPackage("com.waze") // No enviar el favorito a otro navegador ni abrir una tienda.
}

/** Guarda únicamente el favorito solicitado, sin leer ni almacenar direcciones o GPS. */
object NavigationRequestStore {
    private const val PREFS = "navigation_requests"
    private const val LAST_WAZE = "last_dispatched_waze_favorite"

    fun last(context: Context): NavigationDestination? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_WAZE, null)
            ?.let { saved -> NavigationDestination.entries.firstOrNull { it.name == saved } }

    fun recordDispatched(context: Context, target: AppTarget) {
        val destination = target.navigationDestination ?: return
        if (target.launchIntent.`package` != "com.waze") return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_WAZE, destination.name).apply()
    }

    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(LAST_WAZE).apply()
    }
}

internal fun navigationDispatchMessage(target: AppTarget): String =
    "Solicitud de ruta a ${target.navigationDestination?.label} enviada a Waze; viaje activo. " +
        "El destino y el inicio de navegación los resuelve Waze."

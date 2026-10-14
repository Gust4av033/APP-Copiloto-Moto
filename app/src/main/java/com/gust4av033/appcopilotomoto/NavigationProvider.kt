package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.net.Uri

data class NavigationCapabilities(
    val opensSavedDestinations: Boolean,
    val searchesPlaces: Boolean = false,
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
    fun nextTurnAnswer(): String = "Este navegador no ofrece una indicación reciente para APP."
    fun prepare(destination: NavigationDestination): Result<AppTarget>
    fun search(query: String): Result<AppTarget> = Result.failure(UnsupportedOperationException("Este navegador no ofrece búsqueda de lugares."))
}

class WazeNavigationProvider(private val context: Context) : NavigationProvider {
    override fun nextTurnAnswer(): String = WazeScreenAccess.answer(context)
    override val id = "waze"
    override val label = "Waze"
    override val capabilities = NavigationCapabilities(opensSavedDestinations = true, searchesPlaces = true)

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

    override fun search(query: String): Result<AppTarget> = runCatching {
        val launch = Intent(Intent.ACTION_VIEW, Uri.parse(wazePlaceSearchLink(query))).setPackage("com.waze")
        check(launch.resolveActivity(context.packageManager) != null) { "Waze no está disponible para buscar lugares." }
        AppTarget("Búsqueda de $query en Waze", launch, navigationSearchQuery = query)
    }

    private fun intent(destination: NavigationDestination) =
        Intent(Intent.ACTION_VIEW, Uri.parse(WazeNavigationLink.forDestination(destination)))
            .setPackage("com.waze") // No enviar el favorito a otro navegador ni abrir una tienda.
}

enum class NavigationApp(val id: String, val label: String, val packageName: String) {
    WAZE("waze", "Waze", "com.waze"),
    GOOGLE_MAPS("google_maps", "Google Maps", "com.google.android.apps.maps");

    companion object {
        fun fromPackage(packageName: String?) = entries.firstOrNull { it.packageName == packageName }
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: WAZE
    }
}

/** User choice and addresses remain on this installation. No route state is persisted. */
object NavigationPreferences {
    private const val PREFS = "navigation_preferences"
    fun validAddress(address: String): Boolean = address.trim().let { it.isEmpty() || PlaceSearchParser.valid(it) }
    fun selected(context: Context): NavigationApp = NavigationApp.fromId(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("provider", null))
    fun select(context: Context, app: NavigationApp) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("provider", app.id).apply()
    }
    fun address(context: Context, destination: NavigationDestination): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(destination.name.lowercase(), null).orEmpty()
    fun saveAddress(context: Context, destination: NavigationDestination, address: String) {
        val value = address.trim()
        require(validAddress(value)) { "Escribí una dirección de 2 a 160 caracteres." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(destination.name.lowercase(), value).apply()
    }
}

class GoogleMapsNavigationProvider(private val context: Context) : NavigationProvider {
    override val id = NavigationApp.GOOGLE_MAPS.id
    override val label = NavigationApp.GOOGLE_MAPS.label
    override val capabilities = NavigationCapabilities(opensSavedDestinations = false, searchesPlaces = true)

    override fun available(): Boolean = googleMapsSearchIntent("gasolinera").resolveActivity(context.packageManager) != null

    override fun prepare(destination: NavigationDestination): Result<AppTarget> = runCatching {
        val address = NavigationPreferences.address(context, destination)
        if (address.isBlank()) {
            // Maps does not expose a documented deep link to the user's personal Home/Work.
            // Show search results inside Maps; only the rider can identify the saved place.
            val launch = googleMapsSearchIntent(destination.label)
            check(launch.resolveActivity(context.packageManager) != null) { "Google Maps no está disponible para buscar ${destination.label}." }
            AppTarget("Elegir ${destination.label} guardado en $label", launch,
                navigationSearchQuery = destination.label)
        } else {
            val launch = googleMapsNavigationIntent(address)
            check(launch.resolveActivity(context.packageManager) != null) { "Google Maps no está disponible para abrir la ruta." }
            AppTarget("Ruta a ${destination.label} en $label", launch, navigationDestination = destination)
        }
    }

    override fun search(query: String): Result<AppTarget> = runCatching {
        require(PlaceSearchParser.valid(query)) { "Pedí un lugar breve, de hasta 160 caracteres." }
        val launch = googleMapsSearchIntent(query)
        check(launch.resolveActivity(context.packageManager) != null) { "Google Maps no está disponible para buscar lugares." }
        AppTarget("Búsqueda de $query en $label", launch, navigationSearchQuery = query)
    }

}

internal fun googleMapsNavigationUri(address: String): Uri = Uri.parse("google.navigation:q=${Uri.encode(address)}")
internal fun googleMapsSearchUri(query: String): Uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
internal fun googleMapsNavigationIntent(address: String) = Intent(Intent.ACTION_VIEW, googleMapsNavigationUri(address))
    .setPackage(NavigationApp.GOOGLE_MAPS.packageName)
internal fun googleMapsSearchIntent(query: String) = Intent(Intent.ACTION_VIEW, googleMapsSearchUri(query))
    .setPackage(NavigationApp.GOOGLE_MAPS.packageName)

/** Guarda únicamente el favorito solicitado, sin leer ni almacenar direcciones o GPS. */
object NavigationRequestStore {
    private const val PREFS = "navigation_requests"
    private const val LAST_WAZE = "last_dispatched_waze_favorite"
    private const val LAST_MAPS = "last_dispatched_google_maps_favorite"

    private fun key(app: NavigationApp) = if (app == NavigationApp.WAZE) LAST_WAZE else LAST_MAPS

    fun last(context: Context, app: NavigationApp = NavigationApp.WAZE): NavigationDestination? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(app), null)
            ?.let { saved -> NavigationDestination.entries.firstOrNull { it.name == saved } }

    fun recordDispatched(context: Context, target: AppTarget,
                         sessionId: String? = TripNavigationState.context.sessionId) {
        val app = NavigationApp.fromPackage(target.launchIntent.`package`) ?: return
        val destination = target.navigationDestination
        val query = target.navigationSearchQuery
        if (destination == null && query.isNullOrBlank()) return
        // Do not let an old session replace the current trip or its repeat favorite.
        if (sessionId != null && sessionId != TripNavigationState.context.sessionId) return
        TripNavigationState.record(sessionId, NavigationDispatch(app.id,
            if (query != null) NavigationRequestKind.SEARCH else NavigationRequestKind.FAVORITE,
            query ?: checkNotNull(destination).label, System.currentTimeMillis()))
        if (destination != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(app), destination.name).apply()
    }

    fun forget(context: Context, app: NavigationApp = NavigationApp.WAZE) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(app)).apply()
    }
}

internal fun navigationDispatchMessage(target: AppTarget): String {
    val label = NavigationApp.fromPackage(target.launchIntent.`package`)?.label ?: "el navegador"
    return if (target.navigationSearchQuery != null)
        "Búsqueda de ${target.navigationSearchQuery} enviada a $label; viaje activo. Elegí el resultado en $label con la moto detenida."
    else "Solicitud de ruta a ${target.navigationDestination?.label} enviada a $label; viaje activo. " +
        "El destino y el inicio de navegación los resuelve $label."
}

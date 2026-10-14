package com.gust4av033.appcopilotomoto

/** Destinos semánticos; cada proveedor decide cómo abrir sus favoritos. */
enum class NavigationDestination(val label: String) { HOME("Casa"), WORK("Trabajo") }

enum class NavigationRequest {
    HOME, WORK, LAST;

    fun resolve(last: NavigationDestination?): NavigationDestination? = when (this) {
        HOME -> NavigationDestination.HOME
        WORK -> NavigationDestination.WORK
        LAST -> last
    }
}

object NavigationCommandParser {
    fun explicitProvider(normalized: String): NavigationApp? = when {
        normalized.endsWith(" en waze") -> NavigationApp.WAZE
        normalized.endsWith(" en google maps") -> NavigationApp.GOOGLE_MAPS
        else -> null
    }

    fun parse(normalized: String): NavigationRequest? {
        val text = normalized.removeSuffix(" en waze").removeSuffix(" en google maps").trim()
        return when (text) {
            "vamos a casa", "vamos a la casa", "vamos para casa", "vamos para la casa",
            "ir a casa", "llevame a casa", "navega a casa", "navegar a casa",
            "regresa a casa", "volver a casa", "take me home", "go home", "let s go home" -> NavigationRequest.HOME
            "vamos al trabajo", "vamos a trabajo", "vamos para el trabajo", "ir al trabajo",
            "llevame al trabajo", "navega al trabajo", "navegar al trabajo",
            "regresa al trabajo", "volver al trabajo", "vamos a la chamba", "llevame a la chamba", "take me to work", "go to work" -> NavigationRequest.WORK
            "vamos al ultimo destino", "ir al ultimo destino", "llevame al ultimo destino",
            "vamos a la ultima ubicacion", "vamos a la ultima ubicacion puesta",
            "ultima ubicacion", "ultimo destino", "repite el ultimo destino",
            "volvamos al ultimo destino" -> NavigationRequest.LAST
            else -> null
        }
    }
}

/** Repetir una solicitud no implica conocer la ruta ni la dirección guardada por Waze. */
object WazeNavigationLink {
    fun forDestination(destination: NavigationDestination): String {
        val favorite = when (destination) {
            NavigationDestination.HOME -> "home"
            NavigationDestination.WORK -> "work"
        }
        return "https://waze.com/ul?favorite=$favorite&navigate=yes&utm_source=app_copiloto_moto"
    }
}


package com.gust4av033.appcopilotomoto

enum class PointOfInterestCategory {
    GASOLINERA, TALLER_MOTO, COMIDA_PARADA, FARMACIA, CAJERO, PERSONALIZADO;

    companion object {
        fun fromQuery(query: String): PointOfInterestCategory {
            val words = MinimalIntentRouter.normalize(query)
            return when {
                Regex("\\b(gasolinera|gasolineras|gasolina)\\b").containsMatchIn(words) -> GASOLINERA
                Regex("\\b(taller|talleres|mecanico|mecanica)\\b").containsMatchIn(words) -> TALLER_MOTO
                Regex("\\b(comida|restaurante|parada de comida)\\b").containsMatchIn(words) -> COMIDA_PARADA
                Regex("\\b(farmacia|farmacias)\\b").containsMatchIn(words) -> FARMACIA
                Regex("\\b(cajero|cajeros|atm)\\b").containsMatchIn(words) -> CAJERO
                else -> PERSONALIZADO
            }
        }
    }
}

/** Contracts for phase 7. Providers may return null when offline or when evidence is stale. */
interface WeatherProvider {
    suspend fun at(latitude: Double, longitude: Double, epochMs: Long): ContextValue<WeatherObservation>?
}

data class WeatherObservation(val temperatureCelsius: Double?, val condition: String)

data class PoiPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

data class PoiQuery(val category: PointOfInterestCategory, val near: PoiPoint? = null,
    val route: List<PoiPoint> = emptyList(), val radiusMeters: Int = 3_000) {
    init { require((near != null) != route.isNotEmpty()); require(radiusMeters in 100..50_000) }
}

data class PoiResult(val name: String, val position: PoiPoint)

interface PlacesProvider {
    suspend fun search(query: PoiQuery): ContextValue<List<PoiResult>>?
}

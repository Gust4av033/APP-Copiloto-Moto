package com.gust4av033.appcopilotomoto

/** Conservative parser: unmatched text is not presented as a route instruction. */
internal object NavigationObservationParser {
    private val turn = Regex("(?i)\\b(gira|gire|dobla|doble|turn|take|toma|tome|continua|sigue|salida|exit)\\b.{0,80}")
    private val distance = Regex("(?i)\\b(\\d+(?:[.,]\\d+)?)\\s*(km|m|metros|mi|ft)\\b")
    private val duration = Regex("(?i)\\b(\\d{1,3})\\s*(min|minutos|minutes)\\b")
    private val alert = Regex("(?i)\\b(accidente|peligro|alerta|cierre|obras|hazard|crash|closed)\\b")
    fun parse(lines: List<String>, source: ContextSource, epoch: Long, elapsed: Long,
        confidence: Float = if (source in setOf(ContextSource.WAZE_ACCESSIBILITY,
                ContextSource.GOOGLE_MAPS_ACCESSIBILITY)) .85f else .65f): NavigationContext {
        val texts = lines.map { it.trim().take(180) }.filter(String::isNotBlank).distinct().take(12)
        if (texts.isEmpty()) return NavigationContext()
        fun <T> value(item: T?, ttl: Long = 15_000): ContextValue<T>? = item?.let {
            ContextValue(it, source, epoch, elapsed, confidence, ttl)
        }
        val provider = if (source in setOf(ContextSource.GOOGLE_MAPS_NOTIFICATION,
                ContextSource.GOOGLE_MAPS_ACCESSIBILITY)) "Google Maps" else "Waze"
        val instruction = texts.firstOrNull { turn.containsMatchIn(it) }
        val nearby = instruction?.let { texts.indexOf(it) }?.let { index ->
            texts.drop((index - 1).coerceAtLeast(0)).take(3)
        }.orEmpty()
        val turnDistance = nearby.firstNotNullOfOrNull { distance.find(it)?.value }
        val meters = turnDistance?.let(::parseDistanceMeters)
        val minutes = texts.firstNotNullOfOrNull { duration.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val etaLine = texts.firstOrNull { duration.containsMatchIn(it) }
        val remainingDistance = texts.firstOrNull { it != turnDistance && distance.containsMatchIn(it) }
        val street = texts.firstOrNull { Regex("(?i)\\b(calle|avenida|bulevar|autopista|street|road|ave)\\b").containsMatchIn(it) }
        return NavigationContext(value(provider), value(instruction), value(turnDistance), value(meters),
            value(street), value(etaLine), value(minutes), value(remainingDistance),
            value(texts.firstOrNull { alert.containsMatchIn(it) }),
            minutes?.let { EtaObservation(if (provider == "Waze") EtaSource.WAZE else EtaSource.GOOGLE_MAPS,
                it, epoch, elapsed, 15_000) })
    }
    fun parseDistanceMeters(text: String): Int? {
        val match = distance.find(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "km" -> 1000.0; "mi" -> 1609.344; "ft" -> .3048; else -> 1.0
        }
        return (amount * multiplier).toInt()
    }
}

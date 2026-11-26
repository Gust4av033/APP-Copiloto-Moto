package com.gust4av033.appcopilotomoto

/** Our bundled, versioned catalogue. IDs stay stable as artwork/specifications are added.
 * No inferred engine, fuel or service specifications. The first model uses original artwork. */
data class MotorcycleCatalogEntry(val id: String, val model: String, val artwork: Int)
object MotorcycleCatalog {
    val entries = listOf(MotorcycleCatalogEntry("hero_ignitor_125", "Hero Ignitor 125", R.drawable.ic_ignitor_vector))
    fun find(id: String?) = entries.firstOrNull { it.id == id }
}

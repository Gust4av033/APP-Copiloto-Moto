package com.gust4av033.appcopilotomoto

data class MotorcycleCatalogModel(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val model: String
)

object MotorcycleCatalog {
    val models = listOf(
        MotorcycleCatalogModel(
            id = "hero_ignitor_125",
            displayName = "Hero Ignitor 125",
            manufacturer = "Hero",
            model = "Ignitor 125"
        )
    )

    fun find(id: String?): MotorcycleCatalogModel? = models.firstOrNull { it.id == id }
}

package com.gust4av033.appcopilotomoto

/** Values describe the cited 10 L / five-speed variant, not every Ignitor year/market. */
enum class MotorcycleSpec(val label: Int, val maximum: Double, val integer: Boolean = false) {
    DISPLACEMENT(R.string.moto_spec_displacement, 3000.0),
    FUEL(R.string.moto_spec_fuel, 100.0),
    GEARS(R.string.moto_spec_gears, 10.0, true),
    WEIGHT(R.string.moto_spec_weight, 1000.0);
    fun valid(value: String): Boolean = value.isBlank() ||
        Regex("[0-9]{1,4}([.,][0-9]{1,2})?").matches(value) &&
        value.replace(',', '.').toDoubleOrNull()?.let { it > 0 && it <= maximum && (!integer || it % 1 == 0.0) } == true
}
data class MotorcycleCatalogEntry(val id: String, val model: String, val artwork: Int,
    val specifications: Map<String, String> = emptyMap(), val sourceUrl: String? = null)
object MotorcycleCatalog {
    val entries = listOf(MotorcycleCatalogEntry("hero_ignitor_125", "Hero Ignitor 125", R.drawable.ic_ignitor_vector,
        mapOf("DISPLACEMENT" to "124.7", "FUEL" to "10", "GEARS" to "5", "WEIGHT" to "123"),
        "https://heromotos.com.sv/wp-content/uploads/2023/08/EL-SALVADOR_Ignitor125_agosto10_2023.pdf"))
    fun find(id: String?) = entries.firstOrNull { it.id == id }
}
fun validMotorcycleOverrides(values: Map<String, String>) = values.all { (key, value) ->
    MotorcycleSpec.entries.firstOrNull { it.name == key }?.valid(value) == true
}

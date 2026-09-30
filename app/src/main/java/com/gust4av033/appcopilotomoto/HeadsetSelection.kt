package com.gust4av033.appcopilotomoto

/** IDs temporales: nunca reutilizar una selección para un dispositivo diferente. */
internal data class HeadsetIdentity(
    val id: Int,
    val type: Int,
    val address: String,
    val name: String
) {
    fun matches(other: HeadsetIdentity): Boolean = this == other
}

/** Compartida por Activity y servicio; al terminar el proceso vuelve a Automático. */
internal object HeadsetSelection {
    @Volatile
    var preferred: HeadsetIdentity? = null
        private set

    fun select(device: HeadsetIdentity?) { preferred = device }

    fun resolve(available: List<HeadsetIdentity>): HeadsetIdentity? {
        val selected = preferred ?: return null
        return available.singleOrNull(selected::matches)
    }
}

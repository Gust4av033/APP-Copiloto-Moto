package com.gust4av033.appcopilotomoto

import java.security.MessageDigest
import java.util.Locale

/** Señales recibidas en un contexto de casco; ausencia de evidencia no significa ausencia de botones. */
internal data class HeadsetControlEvidence(
    val mediaKeys: Set<Int> = emptySet(),
    val assistantInvocation: Boolean = false
) {
    fun observeKey(keyCode: Int): HeadsetControlEvidence = when (keyCode) {
        219, 231 -> copy(assistantInvocation = true)
        in MEDIA_KEYS -> copy(mediaKeys = mediaKeys + keyCode)
        else -> this // Volumen puede provenir del teléfono; no atribuirlo automáticamente al casco.
    }

    companion object {
        val MEDIA_KEYS = setOf(79, 85, 86, 87, 88, 89, 90, 126, 127)

        fun persistentId(headset: HeadsetIdentity): String? {
            val address = headset.address.trim().lowercase(Locale.ROOT)
            if (address.isBlank()) return null // Nunca guardar por nombre de modelo o ID temporal.
            return MessageDigest.getInstance("SHA-256").digest(address.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
    }
}

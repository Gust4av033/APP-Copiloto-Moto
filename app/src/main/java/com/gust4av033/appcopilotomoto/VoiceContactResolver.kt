package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Solo usa la agenda del dispositivo para resolver el nombre pronunciado en esta orden. */
class VoiceContactResolver(private val context: Context) {
    private data class Candidate(val id: Long, val contact: TripCallContact)

    fun findUnique(spokenName: String): Result<TripCallContact> = runCatching {
        check(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            "Habilitá acceso a contactos desde APP antes de iniciar la sesión."
        }
        val sought = MinimalIntentRouter.normalize(spokenName)
        require(sought.length >= 2) { "Decí el nombre del contacto después de ‘llama a’." }
        val columns = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER)
        val entries = mutableListOf<Candidate>()
        context.contentResolver.query(Phone.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contact = TripCallContact.fromPhone(
                    cursor.getString(nameIndex).orEmpty(), cursor.getString(numberIndex).orEmpty()
                ) ?: continue
                entries += Candidate(cursor.getLong(idIndex), contact)
            }
        } ?: error("No se pudo consultar la agenda del teléfono.")

        val exact = entries.filter { MinimalIntentRouter.normalize(it.contact.name) == sought }
        val matches = exact.ifEmpty {
            entries.filter { MinimalIntentRouter.normalize(it.contact.name).startsWith("$sought ") }
        }.distinctBy { it.id to it.contact.number }

        require(matches.isNotEmpty()) { "No encontré ‘$spokenName’ en tus contactos con teléfono." }
        require(matches.map { it.id }.distinct().size == 1) {
            "Hay varios contactos llamados ‘$spokenName’. Decí el nombre completo."
        }
        require(matches.map { it.contact.number }.distinct().size == 1) {
            "Ese contacto tiene varios números. Elegí uno desde APP para esta prueba."
        }
        matches.first().contact
    }
}

package com.gust4av033.appcopilotomoto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Solo usa la agenda del dispositivo para resolver el nombre pronunciado en esta orden. */
class VoiceContactResolver(private val context: Context) {
    fun findUnique(spokenName: String, preferInternationalNumber: Boolean = false): Result<TripCallContact> = runCatching {
        check(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            "Habilitá acceso a contactos desde APP antes de iniciar la sesión."
        }
        val columns = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.NORMALIZED_NUMBER)
        val entries = mutableListOf<PhoneContactCandidate>()
        context.contentResolver.query(Phone.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            val internationalIndex = cursor.getColumnIndexOrThrow(Phone.NORMALIZED_NUMBER)
            while (cursor.moveToNext()) {
                val raw = cursor.getString(numberIndex).orEmpty()
                val international = cursor.getString(internationalIndex).orEmpty()
                val number = if (preferInternationalNumber && international.matches(Regex("\\+[1-9][0-9]{6,14}"))) international else raw
                val contact = TripCallContact.fromPhone(cursor.getString(nameIndex).orEmpty(), number) ?: continue
                entries += PhoneContactCandidate(cursor.getLong(idIndex), contact)
            }
        } ?: error("No se pudo consultar la agenda del teléfono.")

        ContactNameMatcher.findUnique(spokenName, entries)
    }
}


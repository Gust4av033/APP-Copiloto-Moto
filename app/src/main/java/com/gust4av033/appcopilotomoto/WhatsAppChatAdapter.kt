package com.gust4av033.appcopilotomoto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** Enlace oficial click-to-chat, dirigido exclusivamente a la edición solicitada. */
class WhatsAppChatAdapter(private val context: Context, override val app: ChatApp) : ContactChatAdapter {
    private fun intentFor(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)

    @Suppress("DEPRECATION")
    private fun canHandle(intent: Intent): Boolean = context.packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).any { info ->
            val activity = info.activityInfo
            activity != null && activity.packageName == app.packageName && activity.enabled && activity.exported &&
                (activity.permission.isNullOrEmpty() || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)
        }

    override fun available(): Boolean = runCatching { canHandle(intentFor("https://wa.me/15551234567")) }.getOrDefault(false)

    override fun prepare(contact: TripCallContact): Result<AppTarget> = runCatching {
        check(available()) { "${app.label} no está disponible para abrir chats. Revisá Ajustes → Integraciones." }
        // Usa el número internacional de la agenda o la región de la SIM; nunca agrega +503 por su cuenta.
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val region = runCatching { telephony?.simCountryIso.orEmpty().uppercase(Locale.ROOT) }.getOrDefault("")
        val number = PhoneNumberUtils.formatNumberToE164(contact.number, region.ifEmpty { "ZZ" })
            ?: error("No se pudo validar el país/número de ${contact.name}. Guardalo con + y código de país en Contactos.")
        val intent = intentFor(WhatsAppChatLink.fromInternationalNumber(number))
        check(canHandle(intent)) { "${app.label} no ofrece una apertura compatible para ese chat." }
        AppTarget("${app.label} · chat con ${contact.display}", intent,
            spokenLabel = "el chat de ${contact.name} en ${app.label}. Número terminado en ${number.takeLast(4).toCharArray().joinToString(" ")}")
    }
}

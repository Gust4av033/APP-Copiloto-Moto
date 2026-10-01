package com.gust4av033.appcopilotomoto

/** Contrato de apertura pública de chats; nuevos proveedores pueden implementar este adaptador. */
interface ContactChatAdapter {
    val app: ChatApp
    fun available(): Boolean
    fun prepare(contact: TripCallContact): Result<AppTarget>
}

package com.gust4av033.appcopilotomoto

/** Se puede sustituir el motor o el modelo sin cambiar el flujo de órdenes. */
interface WakeWordProvider {
    val phrase: String
    fun start(onDetected: (Float) -> Unit): Result<Unit>
    fun pause()
    fun close()
}

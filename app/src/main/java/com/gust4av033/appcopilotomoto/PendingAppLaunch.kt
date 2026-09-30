package com.gust4av033.appcopilotomoto

/** Una solicitud reemplaza a la anterior; callbacks viejos no cancelan una solicitud nueva. */
internal class PendingAppLaunch<T> {
    data class Request<T>(val token: String, val target: T, val expiresAtMs: Long)

    var current: Request<T>? = null
        private set

    fun prepare(target: T, token: String, nowMs: Long): Request<T> =
        Request(token, target, nowMs + VALIDITY_MS).also { current = it }

    fun clear(token: String? = null): Boolean {
        val request = current ?: return false
        if (token != null && request.token != token) return false
        current = null
        return true
    }

    fun expire(token: String, nowMs: Long): Boolean {
        val request = current ?: return false
        if (request.token != token || nowMs < request.expiresAtMs) return false
        return clear(token)
    }

    companion object { const val VALIDITY_MS = 30_000L }
}

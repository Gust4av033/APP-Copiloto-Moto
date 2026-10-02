package com.gust4av033.appcopilotomoto

/** Una solicitud reemplaza a la anterior; callbacks viejos no cancelan una solicitud nueva. */
internal class PendingAppLaunch<T> {
    data class Request<T>(val token: String, val target: T, val expiresAtMs: Long)

    var current: Request<T>? = null
        private set

    fun prepare(target: T, token: String, nowMs: Long, validityMs: Long = VALIDITY_MS): Request<T> {
        require(validityMs in 1..DRAFT_VALIDITY_MS)
        return Request(token, target, nowMs + validityMs).also { current = it }
    }

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

    companion object {
        const val VALIDITY_MS = 30_000L
        const val DRAFT_VALIDITY_MS = 60_000L
    }
}

package org.openfsp.gateway.providers

import org.openfsp.server.ExchangeRedaction

class SecretRedaction(
    held: Collection<String?>,
    private val current: () -> Collection<String?> = { emptyList() },
) {
    private val held = held.filterNotNull()

    fun redact(detail: Map<String, Any?>): Map<String, Any?> {
        val secrets = (held + current().filterNotNull()).filter { it.isNotEmpty() }.sortedByDescending { it.length }
        return detail.mapValues { (_, value) -> redact(value, secrets) }
    }

    private fun redact(
        value: Any?,
        secrets: List<String>,
    ): Any? =
        when (value) {
            is String -> secrets.fold(value) { text, secret -> text.replace(secret, ExchangeRedaction.MASK) }
            is Map<*, *> -> value.entries.associate { (key, inner) -> key.toString() to redact(inner, secrets) }
            is List<*> -> value.map { redact(it, secrets) }
            else -> value
        }
}

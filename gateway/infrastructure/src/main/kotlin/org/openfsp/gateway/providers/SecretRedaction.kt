package org.openfsp.gateway.providers

class SecretRedaction(
    secrets: Collection<String?>,
) {
    private val secrets = secrets.filterNotNull().filter { it.length >= MIN_SECRET }.sortedByDescending { it.length }

    fun redact(detail: Map<String, Any?>): Map<String, Any?> = detail.mapValues { (_, value) -> redact(value) }

    private fun redact(value: Any?): Any? =
        when (value) {
            is String -> secrets.fold(value) { text, secret -> text.replace(secret, REDACTED) }
            is Map<*, *> -> value.entries.associate { (key, inner) -> key.toString() to redact(inner) }
            is List<*> -> value.map(::redact)
            else -> value
        }

    private companion object {
        const val REDACTED = "[redacted]"
        const val MIN_SECRET = 4
    }
}

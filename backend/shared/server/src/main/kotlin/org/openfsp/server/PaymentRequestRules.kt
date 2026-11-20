package org.openfsp.server

import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.format.DateTimeParseException

class PaymentRequestRules {
    fun isPhoneNumber(value: String): Boolean = E164.matches(value)

    fun isDescription(value: String): Boolean = value.codePointCount(0, value.length) <= MAX_DESCRIPTION

    fun metadata(
        entries: Map<String, Any?>,
        errors: MutableList<FieldError>,
    ): Map<String, String> {
        if (entries.size > MAX_METADATA_KEYS) errors += FieldError(JsonPointer.of("metadata"), FieldErrorCode.TOO_MANY, "must have at most $MAX_METADATA_KEYS keys")
        return entries
            .mapNotNull { (key, value) ->
                when {
                    !METADATA_KEY.matches(key) ->
                        null.also { errors += FieldError(JsonPointer.of("metadata", key), FieldErrorCode.MALFORMED, "key must be 1 to $MAX_METADATA_KEY_LENGTH characters among A-Za-z0-9_.-") }
                    value !is String -> null.also { errors += FieldError(JsonPointer.of("metadata", key), FieldErrorCode.INVALID_TYPE, "must be a string") }
                    value.codePointCount(0, value.length) > MAX_METADATA_VALUE ->
                        null.also { errors += FieldError(JsonPointer.of("metadata", key), FieldErrorCode.TOO_LONG, "must be at most $MAX_METADATA_VALUE characters") }
                    else -> key to value
                }
            }.toMap()
    }

    fun utcTimestamp(value: String): Instant? {
        if (!UTC_TIMESTAMP.matches(value)) return null
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun returnUrl(
        value: String,
        loopbackHttpAllowed: Boolean,
    ): URI? {
        val uri =
            try {
                URI(value)
            } catch (_: URISyntaxException) {
                return null
            }
        if (!uri.isAbsolute || uri.isOpaque || uri.host.isNullOrEmpty()) return null
        val scheme = uri.scheme.lowercase()
        val loopbackHttp = loopbackHttpAllowed && scheme == HTTP && uri.host.lowercase() in LOOPBACK_HTTP_HOSTS
        return uri.takeIf { scheme == HTTPS || loopbackHttp }
    }

    companion object {
        const val MAX_DESCRIPTION = 255
        const val MAX_METADATA_KEYS = 20
        const val MAX_METADATA_KEY_LENGTH = 40
        const val MAX_METADATA_VALUE = 500

        private val E164 = Regex("^\\+[1-9]\\d{1,14}$")
        private val METADATA_KEY = Regex("^[A-Za-z0-9_.-]{1,$MAX_METADATA_KEY_LENGTH}$")
        private const val HTTPS = "https"
        private const val HTTP = "http"
        private val LOOPBACK_HTTP_HOSTS = setOf("localhost", "127.0.0.1")
        private val UTC_TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d:([0-5]\\d|60)(\\.\\d{1,3})?Z$")
    }
}

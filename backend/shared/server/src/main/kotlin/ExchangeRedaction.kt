package org.openfsp.server

object ExchangeRedaction {
    const val MASK = "[REDACTED]"
    private val SECRET_HEADER = Regex("(?i)(password|secret|token|signature|private[_-]?key|api[_-]?key|authorization|cookie|credential)")
    private val SECRET_FIELD = Regex("(?i)(password|secret|token|signature|key|authorization|cookie|credential)")
    private val JSON_FIELD = Regex("(\"([A-Za-z0-9_.-]+)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"")
    private val PAIR = Regex("(^|[?&])([^=&]+)=([^&]*)")

    fun headers(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) -> if (SECRET_HEADER.containsMatchIn(name)) MASK else value }

    fun body(body: String): String {
        val masked = JSON_FIELD.replace(body) { match -> if (SECRET_FIELD.containsMatchIn(match.groupValues[2])) "${match.groupValues[1]}\"$MASK\"" else match.value }
        return if (masked.trimStart().startsWith("{") || masked.trimStart().startsWith("[")) masked else pairs(masked)
    }

    fun pairs(text: String): String = PAIR.replace(text) { match -> if (SECRET_FIELD.containsMatchIn(match.groupValues[2])) "${match.groupValues[1]}${match.groupValues[2]}=$MASK" else match.value }
}

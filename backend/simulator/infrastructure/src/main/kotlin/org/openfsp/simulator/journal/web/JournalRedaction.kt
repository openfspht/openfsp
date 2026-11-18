package org.openfsp.simulator.journal.web

import org.openfsp.protocol.ExchangeRedaction
import org.springframework.stereotype.Component

@Component
class JournalRedaction {
    fun headers(headers: Map<String, String>): Map<String, String> = ExchangeRedaction.headers(headers)

    fun body(body: String): String {
        val masked = JSON_FIELD.replace(ExchangeRedaction.body(body)) { match -> if (SECRET.containsMatchIn(match.groupValues[2])) "${match.groupValues[1]}\"${ExchangeRedaction.MASK}\"" else match.value }
        return if (masked.trimStart().startsWith("{") || masked.trimStart().startsWith("[")) masked else pairs(masked)
    }

    fun pairs(text: String): String =
        PAIR.replace(ExchangeRedaction.pairs(text)) { match -> if (SECRET.containsMatchIn(match.groupValues[2])) "${match.groupValues[1]}${match.groupValues[2]}=${ExchangeRedaction.MASK}" else match.value }

    private companion object {
        val SECRET = Regex("(?i)(key|secret|token)")
        val JSON_FIELD = Regex("(\"([A-Za-z0-9_.-]+)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"")
        val PAIR = Regex("(^|[?&])([^=&]+)=([^&]*)")
    }
}

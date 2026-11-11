package org.openfsp.validator.engine

import java.time.Duration

object Durations {
    private val pattern = Regex("^(\\d+)(ms|s|m)$")

    fun parse(text: String): Duration {
        val match = pattern.matchEntire(text.trim()) ?: throw IllegalArgumentException("duration $text must look like 500ms, 10s or 2m")
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            else -> Duration.ofMinutes(amount)
        }
    }
}

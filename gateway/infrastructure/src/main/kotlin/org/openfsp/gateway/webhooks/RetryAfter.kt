package org.openfsp.gateway.webhooks

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.util.Locale

object RetryAfter {
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val TWO_DIGIT_YEAR_HORIZON = 50L
    private const val CENTURY = 100L

    private val IMF_FIXDATE = strict("EEE, dd MMM uuuu HH:mm:ss 'GMT'")
    private val ASCTIME = strict("EEE MMM ppd HH:mm:ss uuuu")
    private val RFC_850 =
        DateTimeFormatterBuilder()
            .appendPattern("dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, TWO_DIGIT_YEAR_BASE)
            .appendPattern(" HH:mm:ss 'GMT'")
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC)
            .withResolverStyle(ResolverStyle.STRICT)

    fun parse(
        value: String,
        now: Instant,
    ): Duration? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.all(Char::isDigit)) return Duration.ofSeconds(trimmed.toLongOrNull() ?: Long.MAX_VALUE)
        val date = httpDate(trimmed, now) ?: return null
        return Duration.between(now, date).coerceAtLeast(Duration.ZERO)
    }

    private fun httpDate(
        value: String,
        now: Instant,
    ): Instant? {
        parse(value, IMF_FIXDATE)?.let { return it.toInstant() }
        parse(value, ASCTIME)?.let { return it.toInstant() }
        val parsed = parse(value.substringAfter(", "), RFC_850) ?: return null
        val horizon = now.atZone(ZoneOffset.UTC).plusYears(TWO_DIGIT_YEAR_HORIZON)
        val rfc850 = if (parsed.isAfter(horizon)) parsed.minusYears(CENTURY) else parsed
        val weekday = rfc850.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        return rfc850.toInstant().takeIf { value.startsWith("$weekday, ") }
    }

    private fun parse(
        value: String,
        format: DateTimeFormatter,
    ): ZonedDateTime? = runCatching { ZonedDateTime.parse(value, format) }.getOrNull()

    private fun strict(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.US).withZone(ZoneOffset.UTC).withResolverStyle(ResolverStyle.STRICT)
}

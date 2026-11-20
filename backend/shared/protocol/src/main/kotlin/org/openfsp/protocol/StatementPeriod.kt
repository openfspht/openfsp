package org.openfsp.protocol

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class StatementPeriod(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(!to.isBefore(from)) { "a period ends on or after its first day" }
        require(ChronoUnit.DAYS.between(from, to) < MAX_DAYS) { "a period covers at most $MAX_DAYS business days" }
    }

    sealed interface Request {
        data class Accepted(
            val period: StatementPeriod,
        ) : Request

        data object Missing : Request

        data class Invalid(
            val parameter: String,
            val detail: String,
        ) : Request
    }

    companion object {
        const val MAX_DAYS = 31L
        const val BUSINESS_DATE = "business_date"
        const val FROM = "from"
        const val TO = "to"
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
        const val DEFAULT_LIMIT = 50

        fun of(
            businessDate: String?,
            from: String?,
            to: String?,
        ): Request {
            val day = businessDate?.takeIf { it.isNotBlank() }
            val first = from?.takeIf { it.isNotBlank() }
            val last = to?.takeIf { it.isNotBlank() }
            return when {
                day == null && first == null && last == null -> Request.Missing
                day != null && (first != null || last != null) -> Request.Invalid(BUSINESS_DATE, "cannot be combined with $FROM or $TO")
                day != null -> single(day)
                first == null -> Request.Invalid(FROM, "is required with $TO")
                last == null -> Request.Invalid(TO, "is required with $FROM")
                else -> range(first, last)
            }
        }

        private fun single(day: String): Request {
            val date = parse(day) ?: return Request.Invalid(BUSINESS_DATE, NOT_A_DATE)
            return Request.Accepted(StatementPeriod(date, date))
        }

        private fun range(
            first: String,
            last: String,
        ): Request {
            val from = parse(first) ?: return Request.Invalid(FROM, NOT_A_DATE)
            val to = parse(last) ?: return Request.Invalid(TO, NOT_A_DATE)
            return when {
                to.isBefore(from) -> Request.Invalid(TO, "must not precede $FROM")
                ChronoUnit.DAYS.between(from, to) >= MAX_DAYS -> Request.Invalid(TO, "a range covers at most $MAX_DAYS business days")
                else -> Request.Accepted(StatementPeriod(from, to))
            }
        }

        private fun parse(value: String): LocalDate? =
            try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }

        private const val NOT_A_DATE = "must be a full-date"
    }
}

package org.openfsp.sdk

import org.openfsp.protocol.StatementPeriod
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.sdk.http.Api
import org.openfsp.sdk.model.StatementPage
import org.openfsp.sdk.protocol.Mapping
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class Statements internal constructor(
    private val api: Api,
) {
    @JvmOverloads
    fun forDay(
        provider: String,
        businessDate: LocalDate,
        limit: Int? = null,
        cursor: String? = null,
    ): StatementPage = page(provider, mapOf(StatementPeriod.BUSINESS_DATE to businessDate.toString()), limit, cursor)

    @JvmOverloads
    fun between(
        provider: String,
        from: LocalDate,
        to: LocalDate,
        limit: Int? = null,
        cursor: String? = null,
    ): StatementPage {
        if (to.isBefore(from)) throw InvalidRequestException("to must not precede from")
        if (ChronoUnit.DAYS.between(from, to) >= StatementPeriod.MAX_DAYS) throw InvalidRequestException("a range covers at most ${StatementPeriod.MAX_DAYS} business days")
        return page(provider, mapOf(StatementPeriod.FROM to from.toString(), StatementPeriod.TO to to.toString()), limit, cursor)
    }

    private fun page(
        provider: String,
        period: Map<String, String>,
        limit: Int?,
        cursor: String?,
    ): StatementPage {
        if (provider.isBlank()) throw InvalidRequestException("provider must not be empty")
        if (limit != null && limit !in StatementPeriod.MIN_LIMIT..StatementPeriod.MAX_LIMIT) {
            throw InvalidRequestException("limit must be from ${StatementPeriod.MIN_LIMIT} to ${StatementPeriod.MAX_LIMIT}")
        }
        if (cursor != null && cursor.isEmpty()) throw InvalidRequestException("cursor must not be empty")
        val paging = listOfNotNull(limit?.let { "limit" to it.toString() }, cursor?.let { "cursor" to it })
        return Mapping.toStatementPage(api.get(STATEMENTS, StatementCollection::class.java, mapOf("provider" to provider) + period + paging))
    }

    private companion object {
        const val STATEMENTS = "v1/statements"
    }
}

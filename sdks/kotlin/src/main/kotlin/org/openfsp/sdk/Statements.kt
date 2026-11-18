package org.openfsp.sdk

import org.openfsp.protocol.api.StatementCollection
import org.openfsp.sdk.http.Api
import org.openfsp.sdk.model.Statement
import org.openfsp.sdk.protocol.Mapping
import java.time.LocalDate

class Statements internal constructor(
    private val api: Api,
) {
    fun find(
        provider: String,
        businessDate: LocalDate,
    ): Statement? {
        if (provider.isBlank()) throw InvalidRequestException("provider must not be empty")
        val query = mapOf("provider" to provider, "business_date" to businessDate.toString())
        return api.get(STATEMENTS, StatementCollection::class.java, query).data.firstOrNull()?.let(Mapping::toStatement)
    }

    private companion object {
        const val STATEMENTS = "v1/statements"
    }
}

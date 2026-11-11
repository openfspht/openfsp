package org.openfsp.gateway.statement.api

import org.openfsp.gateway.statement.EntryCursor
import org.openfsp.gateway.statement.EntryPage
import org.openfsp.gateway.statement.GatewayStatement
import org.openfsp.gateway.statement.Match
import org.openfsp.gateway.statement.OperatorStatement
import java.time.LocalDate

interface ReconcileStatements {
    fun fetchRecent()

    fun fetch(
        provider: String,
        day: LocalDate,
    )
}

interface ReadStatements {
    fun operatorStatement(
        provider: String,
        day: LocalDate,
    ): OperatorStatement?

    fun entries(
        match: Match,
        provider: String?,
        limit: Int,
        after: EntryCursor?,
    ): EntryPage

    fun gatewayStatement(
        provider: String,
        day: LocalDate,
    ): GatewayStatement
}

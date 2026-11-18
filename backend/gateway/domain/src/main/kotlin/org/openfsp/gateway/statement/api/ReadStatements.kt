package org.openfsp.gateway.statement.api

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.gateway.statement.StatementPage
import org.openfsp.protocol.StatementPeriod

interface ReadStatements {
    fun page(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage

    fun all(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
    ): Sequence<StatementEntry>
}

package org.openfsp.gateway.statement.spi

import org.openfsp.gateway.statement.EntryCursor
import org.openfsp.gateway.statement.Match
import org.openfsp.gateway.statement.StatementEntry
import java.time.Instant
import java.time.LocalDate

interface StatementStore {
    fun isComplete(
        provider: String,
        day: LocalDate,
    ): Boolean

    fun open(
        provider: String,
        day: LocalDate,
        at: Instant,
    ): String

    fun find(
        provider: String,
        day: LocalDate,
    ): Pair<String, Boolean>?

    fun hasEntry(
        statementId: String,
        providerReference: String,
    ): Boolean

    fun add(
        statementId: String,
        entry: StatementEntry,
    )

    fun complete(statementId: String)

    fun entries(statementId: String): List<StatementEntry>

    fun page(
        match: Match,
        provider: String?,
        after: EntryCursor?,
        limit: Int,
    ): List<Pair<Long, StatementEntry>>
}

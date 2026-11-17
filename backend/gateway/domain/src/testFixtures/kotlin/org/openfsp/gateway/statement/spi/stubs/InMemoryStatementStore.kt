package org.openfsp.gateway.statement.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.statement.EntryCursor
import org.openfsp.gateway.statement.Match
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.gateway.statement.spi.StatementStore
import java.time.Instant
import java.time.LocalDate

@Stub
class InMemoryStatementStore : StatementStore {
    private data class Statement(
        val id: String,
        val complete: Boolean,
    )

    private val statements = linkedMapOf<Pair<String, LocalDate>, Statement>()
    private val entries = mutableListOf<Pair<String, StatementEntry>>()
    val locks = mutableListOf<Pair<String, LocalDate>>()

    override fun lock(
        provider: String,
        day: LocalDate,
    ) {
        locks += provider to day
    }

    override fun isComplete(
        provider: String,
        day: LocalDate,
    ): Boolean = statements[provider to day]?.complete == true

    override fun open(
        provider: String,
        day: LocalDate,
        at: Instant,
    ): String = statements.getOrPut(provider to day) { Statement("stm_${statements.size + 1}", false) }.id

    override fun find(
        provider: String,
        day: LocalDate,
    ): Pair<String, Boolean>? = statements[provider to day]?.let { it.id to it.complete }

    override fun hasEntry(
        statementId: String,
        providerReference: String,
    ): Boolean = entries.any { (id, entry) -> id == statementId && entry.providerReference == providerReference }

    override fun add(
        statementId: String,
        entry: StatementEntry,
    ) {
        entries += statementId to entry
    }

    override fun complete(statementId: String) {
        statements.replaceAll { _, statement -> if (statement.id == statementId) statement.copy(complete = true) else statement }
    }

    override fun entries(statementId: String): List<StatementEntry> = entries.filter { it.first == statementId }.map { it.second }

    override fun page(
        match: Match,
        provider: String?,
        after: EntryCursor?,
        limit: Int,
    ): List<Pair<Long, StatementEntry>> =
        entries
            .mapIndexed { index, (_, entry) -> index.toLong() + 1 to entry }
            .filter { (position, entry) -> entry.match == match && (after == null || position > after.position) }
            .take(limit)
}

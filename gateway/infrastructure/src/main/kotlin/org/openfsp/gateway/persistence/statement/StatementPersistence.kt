package org.openfsp.gateway.persistence.statement

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.statement.EntryCursor
import org.openfsp.gateway.statement.Match
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.gateway.statement.spi.StatementStore
import org.openfsp.protocol.Money
import org.openfsp.protocol.ResourceIds
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "statements")
class StatementJpaEntity(
    @Id val id: String,
    val provider: String,
    val businessDate: LocalDate,
    var complete: Boolean,
    var fetchedAt: Instant,
)

@Entity
@Table(name = "statement_entries")
class StatementEntryJpaEntity(
    val statementId: String,
    val providerReference: String,
    val type: String,
    val amount: Long,
    val feeAmount: Long?,
    val netAmount: Long?,
    val bookedAt: Instant,
    val providerOrderId: String?,
    val paymentId: String?,
    val match: String,
    val source: String = "provider",
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

interface StatementJpaRepository : JpaRepository<StatementJpaEntity, String> {
    @Query("select count(*) from (select 1 from statements limit :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    fun findByOrderByFetchedAtDesc(limit: Limit): List<StatementJpaEntity>

    fun findByProviderAndBusinessDate(
        provider: String,
        businessDate: LocalDate,
    ): StatementJpaEntity?
}

interface StatementEntryJpaRepository : JpaRepository<StatementEntryJpaEntity, Long> {
    fun existsByStatementIdAndProviderReference(
        statementId: String,
        providerReference: String,
    ): Boolean

    fun findByStatementIdOrderByBookedAtAscIdAsc(statementId: String): List<StatementEntryJpaEntity>

    @Query(
        """
        select e from StatementEntryJpaEntity e
        where e.match = :match and (e.bookedAt > :at or (e.bookedAt = :at and e.id > :id))
        order by e.bookedAt, e.id
        """,
    )
    fun pageAfter(
        match: String,
        at: Instant,
        id: Long,
        limit: Limit,
    ): List<StatementEntryJpaEntity>

    @Query(
        """
        select e from StatementEntryJpaEntity e, StatementJpaEntity s
        where s.id = e.statementId and s.provider = :provider and e.match = :match
          and (e.bookedAt > :at or (e.bookedAt = :at and e.id > :id))
        order by e.bookedAt, e.id
        """,
    )
    fun pageAfterForProvider(
        match: String,
        provider: String,
        at: Instant,
        id: Long,
        limit: Limit,
    ): List<StatementEntryJpaEntity>
}

@Component
class JpaStatementStore(
    private val statements: StatementJpaRepository,
    private val entries: StatementEntryJpaRepository,
) : StatementStore {
    override fun isComplete(
        provider: String,
        day: LocalDate,
    ): Boolean = statements.findByProviderAndBusinessDate(provider, day)?.complete == true

    override fun open(
        provider: String,
        day: LocalDate,
        at: Instant,
    ): String {
        val statement = statements.findByProviderAndBusinessDate(provider, day) ?: StatementJpaEntity(ResourceIds.generate("stm"), provider, day, false, at)
        statement.fetchedAt = at
        return statements.saveAndFlush(statement).id
    }

    override fun find(
        provider: String,
        day: LocalDate,
    ): Pair<String, Boolean>? = statements.findByProviderAndBusinessDate(provider, day)?.let { it.id to it.complete }

    override fun hasEntry(
        statementId: String,
        providerReference: String,
    ): Boolean = entries.existsByStatementIdAndProviderReference(statementId, providerReference)

    override fun add(
        statementId: String,
        entry: StatementEntry,
    ) {
        entries.save(
            StatementEntryJpaEntity(
                statementId,
                entry.providerReference,
                entry.type,
                entry.amount.amount,
                entry.feeAmount?.amount,
                entry.netAmount?.amount,
                entry.bookedAt,
                entry.providerOrderId,
                entry.paymentId,
                entry.match.wire,
            ),
        )
    }

    override fun complete(statementId: String) {
        statements.findById(statementId).ifPresent { statements.save(it.also { statement -> statement.complete = true }) }
    }

    override fun entries(statementId: String): List<StatementEntry> = entries.findByStatementIdOrderByBookedAtAscIdAsc(statementId).map(::toDomain)

    override fun page(
        match: Match,
        provider: String?,
        after: EntryCursor?,
        limit: Int,
    ): List<Pair<Long, StatementEntry>> {
        val at = after?.bookedAt ?: Instant.EPOCH
        val id = after?.position ?: 0L
        val rows =
            if (provider == null) {
                entries.pageAfter(match.wire, at, id, Limit.of(limit))
            } else {
                entries.pageAfterForProvider(match.wire, provider, at, id, Limit.of(limit))
            }
        return rows.map { it.id!! to toDomain(it) }
    }

    private fun toDomain(entity: StatementEntryJpaEntity) =
        StatementEntry(
            providerReference = entity.providerReference,
            type = entity.type,
            amount = Money.htg(entity.amount),
            feeAmount = entity.feeAmount?.let(Money::htg),
            netAmount = entity.netAmount?.let(Money::htg),
            bookedAt = entity.bookedAt,
            providerOrderId = entity.providerOrderId,
            paymentId = entity.paymentId,
            match = Match.fromWire(entity.match)!!,
        )
}

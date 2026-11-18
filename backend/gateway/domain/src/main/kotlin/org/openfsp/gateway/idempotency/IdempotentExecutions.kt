package org.openfsp.gateway.idempotency

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.openfsp.gateway.idempotency.spi.IdempotencyRecords
import org.openfsp.protocol.ProblemType
import java.time.Duration

@DomainService
class IdempotentExecutions(
    private val records: IdempotencyRecords,
    private val transactions: Transactions,
    private val calendar: BusinessCalendar,
) : RunIdempotently {
    override fun <T> run(
        scope: IdempotencyScope,
        fingerprint: String,
        work: () -> T,
        record: (T) -> RecordedResponse,
    ): Execution<T> {
        val generation =
            when (val claim = claim(scope, fingerprint)) {
                is Claim.Replay -> return Execution.Replayed(claim.response)
                is Claim.Lease -> claim.generation
            }
        val value =
            try {
                work()
            } catch (failure: RuntimeException) {
                transactions.inTransaction { records.release(scope, generation) }
                throw failure
            }
        transactions.inTransaction { records.complete(scope, generation, record(value)) }
        return Execution.Fresh(value)
    }

    override fun purgeExpired(
        batch: Int,
        maxBatches: Int,
    ): Int {
        var purged = 0
        repeat(maxBatches) {
            val deleted = transactions.inTransaction { records.purgeCreatedBefore(calendar.now().minus(RETENTION), batch) }
            purged += deleted
            if (deleted < batch) return purged
        }
        return purged
    }

    private sealed interface Claim {
        data class Replay(
            val response: RecordedResponse,
        ) : Claim

        data class Lease(
            val generation: Long,
        ) : Claim
    }

    private fun claim(
        scope: IdempotencyScope,
        fingerprint: String,
    ): Claim =
        transactions.inTransaction {
            val now = calendar.now()
            records.discardCreatedBefore(scope, now.minus(RETENTION))
            if (records.insert(scope, fingerprint, now)) return@inTransaction Claim.Lease(IdempotencyRecord.FIRST_GENERATION)
            val existing = records.find(scope) ?: throw inProgress()
            val next = existing.generation + 1
            when {
                existing.fingerprint != fingerprint -> throw OpenFspException(ProblemType.IDEMPOTENCY_KEY_REUSED)
                existing.response != null -> Claim.Replay(existing.response)
                records.takeOver(scope, fingerprint, now, now.minus(LEASE), existing.generation) -> Claim.Lease(next)
                else -> throw inProgress()
            }
        }

    private fun inProgress() = OpenFspException(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, retryAfter = RETRY_AFTER)

    companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        val LEASE: Duration = Duration.ofMinutes(5)
        private val RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }
}

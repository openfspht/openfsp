package org.openfsp.gateway.idempotency

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.idempotency.persistence.IdempotencyRecordId
import org.openfsp.gateway.idempotency.persistence.IdempotencyRecordRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

sealed interface Claim {
    data object Acquired : Claim

    data class Replay(
        val status: Int,
        val body: ByteArray,
    ) : Claim {
        override fun equals(other: Any?): Boolean = other is Replay && other.status == status && other.body.contentEquals(body)

        override fun hashCode(): Int = 31 * status + body.contentHashCode()
    }

    data object InProgress : Claim

    data object KeyReused : Claim
}

@Component
class Idempotency(
    private val records: IdempotencyRecordRepository,
    private val calendar: BusinessCalendar,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(
        scope: IdempotencyRecordId,
        fingerprint: String,
    ): Claim {
        records.deleteCreatedBefore(calendar.now().minus(RETENTION))
        if (records.claim(scope.principal, scope.operation, scope.idempotencyKey, fingerprint, calendar.now()) == 1) return Claim.Acquired
        val existing = records.findById(scope).orElse(null) ?: return Claim.InProgress
        val status = existing.responseStatus
        val body = existing.responseBody
        return when {
            existing.fingerprint != fingerprint -> Claim.KeyReused
            status == null || body == null -> Claim.InProgress
            else -> Claim.Replay(status, body)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(
        scope: IdempotencyRecordId,
        status: Int,
        body: ByteArray,
    ) {
        records.findById(scope).filter { it.responseStatus == null }.ifPresent {
            it.responseStatus = status
            it.responseBody = body
            records.save(it)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(scope: IdempotencyRecordId) {
        records.release(scope)
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        const val RETRY_AFTER_SECONDS = 1L
    }
}

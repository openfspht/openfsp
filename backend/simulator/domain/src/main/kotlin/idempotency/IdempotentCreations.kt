package org.openfsp.simulator.idempotency

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.idempotency.api.CreateIdempotently
import org.openfsp.simulator.idempotency.spi.IdempotencyRecords
import org.openfsp.simulator.payment.spi.SimulatedPayments

@DomainService
class IdempotentCreations(
    private val records: IdempotencyRecords,
    private val payments: SimulatedPayments,
    private val clock: SimulationClock,
) : CreateIdempotently {
    override fun create(
        scope: IdempotencyScope,
        fingerprint: String,
        attempt: () -> CreationAttempt,
    ): IdempotentCreation {
        var started: IdempotentCreation? = null
        records.compute(scope) { record ->
            when {
                record == null -> IdempotencyRecord.Running(fingerprint)
                record.fingerprint != fingerprint -> record.also { started = IdempotentCreation.Reused }
                record is IdempotencyRecord.Running -> record.also { started = IdempotentCreation.InProgress }
                record is IdempotencyRecord.Done && stillHeld(scope, record.response) -> record.also { started = IdempotentCreation.Replayed(record.response) }
                else -> IdempotencyRecord.Running(fingerprint)
            }
        }
        started?.let { return it }
        val outcome =
            try {
                attempt()
            } catch (failure: RuntimeException) {
                records.remove(scope)
                throw failure
            }
        when (outcome) {
            is CreationAttempt.Determined -> records.save(scope, IdempotencyRecord.Done(fingerprint, outcome.response, clock.now()))
            is CreationAttempt.Undetermined -> records.remove(scope)
        }
        return IdempotentCreation.Answered(outcome.response)
    }

    private fun stillHeld(
        scope: IdempotencyScope,
        response: RecordedResponse,
    ): Boolean = response.paymentId == null || payments.find(response.paymentId)?.takeIf { it.operator == scope.operator } != null
}

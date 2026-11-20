package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Attestation
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component

@Component
class AttestationRequestMapper {
    fun toAttestation(request: AttestationRequest): Attestation {
        val status = PaymentStatus.fromWire(request.status)?.takeIf { it.isTerminal } ?: throw OpenFspException.invalid("status", "must be a terminal state")
        return runCatching { Attestation(request.person, status, request.evidence, request.failureReason?.let(FailureReason::fromWire)) }
            .getOrElse { throw OpenFspException.invalid("attestation", it.message ?: "is invalid") }
    }
}

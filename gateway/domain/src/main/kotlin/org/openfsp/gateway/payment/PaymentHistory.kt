package org.openfsp.gateway.payment

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import java.time.Instant

enum class TransitionSource(
    val wire: String,
) {
    CREATION("creation"),
    LOOKUP("lookup"),
    SIGNED_CALLBACK("signed_callback"),
    PAYMENT_URL("per_payment_url"),
    STATEMENT("statement"),
    ATTESTATION("attestation"),
    ;

    companion object {
        fun fromWire(value: String): TransitionSource = entries.first { it.wire == value }
    }
}

data class Transition(
    val paymentId: String,
    val from: PaymentStatus?,
    val to: PaymentStatus,
    val source: TransitionSource,
    val at: Instant,
)

data class PaymentConflict(
    val paymentId: String,
    val source: TransitionSource,
    val reportedStatus: PaymentStatus,
    val reportedAmount: Money,
    val at: Instant,
)

data class Attestation(
    val person: String,
    val status: PaymentStatus,
    val evidence: String,
    val failureReason: FailureReason? = null,
) {
    init {
        require(status.isTerminal) { "only a terminal state can be attested" }
        require(person.isNotBlank() && evidence.isNotBlank()) { "an attestation names its author and its evidence" }
        require(status == PaymentStatus.FAILED || failureReason == null) { "a failure reason only goes with failed" }
    }
}

data class IndeterminateOperation(
    val paymentId: String,
    val operation: String,
    val error: String,
    val at: Instant,
)

data class PaymentTransitioned(
    val paymentId: String,
    val previous: PaymentStatus?,
    val current: PaymentStatus,
)

sealed interface Observation {
    data class Transitioned(
        val payment: Payment,
        val transition: Transition,
    ) : Observation

    data class Updated(
        val payment: Payment,
    ) : Observation

    data class Conflicted(
        val conflict: PaymentConflict,
    ) : Observation

    data object Unchanged : Observation
}

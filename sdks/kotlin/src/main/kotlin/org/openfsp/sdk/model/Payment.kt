package org.openfsp.sdk.model

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import java.time.Instant

sealed interface NextAction {
    data class Redirect(
        val url: String,
        val expiresAt: Instant?,
    ) : NextAction

    data object PayerApproval : NextAction

    data object None : NextAction
}

data class Fee(
    val amount: Money,
    val bearer: Bearer,
) {
    enum class Bearer { MERCHANT, PAYER }
}

data class Payment(
    val id: String,
    val reference: String,
    val status: PaymentStatus,
    val amount: Money,
    val provider: String,
    val providerOrderId: String,
    val providerReference: String?,
    val payerPhoneNumber: String?,
    val description: String?,
    val nextAction: NextAction,
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant?,
    val completedAt: Instant?,
    val failureReason: FailureReason?,
    val fee: Fee?,
    val metadata: Map<String, String>,
) {
    val isPending: Boolean get() = status == PaymentStatus.PENDING

    val isSuccessful: Boolean get() = status == PaymentStatus.SUCCEEDED

    val isTerminal: Boolean get() = status.isTerminal

    val requiresRedirect: Boolean get() = isPending && nextAction is NextAction.Redirect

    val awaitsPayerApproval: Boolean get() = isPending && nextAction == NextAction.PayerApproval

    val redirectUrl: String? get() = (nextAction as? NextAction.Redirect)?.url

    fun matches(request: PaymentRequest): Boolean = reference == request.reference && amount == request.amount && provider == request.provider
}

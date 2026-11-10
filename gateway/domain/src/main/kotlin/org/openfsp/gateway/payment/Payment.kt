package org.openfsp.gateway.payment

import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Instant

data class Payment(
    val id: String,
    val reference: String,
    val amount: Money,
    val provider: String,
    val providerOrderId: String,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val providerReference: String? = null,
    val payerPhoneNumber: String? = null,
    val description: String? = null,
    val returnUrl: URI? = null,
    val nextAction: NextAction? = null,
    val metadata: Map<String, String>? = null,
    val expiresAt: Instant? = null,
    val completedAt: Instant? = null,
    val failureReason: FailureReason? = null,
    val failureDetail: Map<String, Any?>? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val callbackTokenDigest: String? = null,
) {
    init {
        require(status.isTerminal == (completedAt != null)) { "completed_at is set once, when the payment is terminal" }
        require((status == PaymentStatus.FAILED) == (failureReason != null)) { "failure_reason goes with failed only" }
    }

    val isTerminal: Boolean get() = status.isTerminal

    fun adopt(
        created: ProviderPayment,
        at: Instant,
    ): Payment =
        copy(
            providerReference = created.report.providerReference,
            nextAction = created.nextAction,
            expiresAt = created.expiresAt,
            updatedAt = at,
        )

    fun isExpiredAt(instant: Instant): Boolean = expiresAt != null && instant.isAfter(expiresAt)

    fun observe(
        report: ProviderReport,
        source: TransitionSource,
        at: Instant,
    ): Observation =
        when {
            isTerminal ->
                if (report.status != status && report.status != PaymentStatus.PENDING) Observation.Conflicted(conflict(report, source, at)) else Observation.Unchanged
            report.status == PaymentStatus.PENDING ->
                if (providerReference == null && report.providerReference != null) {
                    Observation.Updated(copy(providerReference = report.providerReference, updatedAt = at))
                } else {
                    Observation.Unchanged
                }
            report.status == PaymentStatus.SUCCEEDED && report.amount != amount -> Observation.Conflicted(conflict(report, source, at))
            else -> settle(report, source, at)
        }

    private fun settle(
        report: ProviderReport,
        source: TransitionSource,
        at: Instant,
    ): Observation.Transitioned {
        check(status.canTransitionTo(report.status)) { "$status cannot move to ${report.status}" }
        val failed = report.status == PaymentStatus.FAILED
        val settled =
            copy(
                status = report.status,
                completedAt = at,
                updatedAt = at,
                nextAction = null,
                providerReference = providerReference ?: report.providerReference,
                failureReason = if (failed) report.failureReason ?: FailureReason.UNSPECIFIED else null,
                failureDetail = if (failed) report.failureDetail else null,
            )
        return Observation.Transitioned(settled, Transition(id, status, report.status, source, at))
    }

    private fun conflict(
        report: ProviderReport,
        source: TransitionSource,
        at: Instant,
    ) = PaymentConflict(id, source, report.status, report.amount, at)
}

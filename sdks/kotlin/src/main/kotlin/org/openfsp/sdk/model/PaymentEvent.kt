package org.openfsp.sdk.model

import org.openfsp.protocol.PaymentStatus
import java.time.Instant

data class PaymentEvent(
    val id: String,
    val type: String,
    val sequence: Long,
    val createdAt: Instant,
    val previousStatus: PaymentStatus?,
    val payment: Payment,
) {
    val reference: String get() = payment.reference

    companion object {
        const val CREATED = "payment.created"
        const val SUCCEEDED = "payment.succeeded"
        const val FAILED = "payment.failed"
        const val EXPIRED = "payment.expired"
        const val CANCELED = "payment.canceled"
    }
}

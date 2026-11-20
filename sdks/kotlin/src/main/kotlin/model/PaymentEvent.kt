package org.openfsp.sdk.model

import org.openfsp.protocol.PaymentStatus
import java.time.Instant

enum class PaymentEventType(
    val wire: String,
    val status: PaymentStatus,
) {
    CREATED("payment.created", PaymentStatus.PENDING),
    SUCCEEDED("payment.succeeded", PaymentStatus.SUCCEEDED),
    FAILED("payment.failed", PaymentStatus.FAILED),
    EXPIRED("payment.expired", PaymentStatus.EXPIRED),
    CANCELED("payment.canceled", PaymentStatus.CANCELED),
    ;

    companion object {
        @JvmStatic
        fun fromWire(value: String): PaymentEventType? = entries.firstOrNull { it.wire == value }

        @JvmStatic
        fun of(status: PaymentStatus): PaymentEventType = entries.first { it.status == status }
    }
}

data class PaymentEvent(
    val id: String,
    val type: PaymentEventType,
    val sequence: Long,
    val createdAt: Instant,
    val previousStatus: PaymentStatus?,
    val payment: Payment,
) {
    val reference: String get() = payment.reference
}

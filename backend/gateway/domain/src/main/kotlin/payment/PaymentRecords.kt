package org.openfsp.gateway.payment

import org.openfsp.protocol.Money
import java.time.Instant

data class PaymentFilters(
    val account: String?,
    val status: String?,
    val provider: String?,
    val query: String?,
)

data class StatusTotal(
    val status: String,
    val count: Long,
    val amount: Money,
)

data class RecordedTransition(
    val id: Long,
    val paymentId: String,
    val fromStatus: String?,
    val toStatus: String,
    val source: String,
    val recordedAt: Instant,
)

data class RecordedConflict(
    val id: Long,
    val paymentId: String,
    val source: String,
    val reported: String,
    val recordedAt: Instant,
)

data class PaymentSummary(
    val id: String,
    val reference: String,
    val provider: String,
    val amount: Money,
    val status: String,
)

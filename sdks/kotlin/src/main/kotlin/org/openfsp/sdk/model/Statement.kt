package org.openfsp.sdk.model

import java.time.Instant
import java.time.LocalDate

data class StatementEntry(
    val provider: String,
    val businessDate: LocalDate,
    val bookedAt: Instant,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val paymentId: String?,
    val reference: String?,
)

data class StatementPage(
    val entries: List<StatementEntry>,
    val nextCursor: String?,
) {
    val hasMore: Boolean get() = nextCursor != null
}

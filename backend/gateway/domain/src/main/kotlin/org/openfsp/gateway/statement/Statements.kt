package org.openfsp.gateway.statement

import org.openfsp.protocol.Money
import java.time.Instant
import java.time.LocalDate

data class StatementEntry(
    val provider: String,
    val businessDate: LocalDate,
    val bookedAt: Instant,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val paymentId: String,
    val reference: String,
)

data class StatementCursor(
    val bookedAt: Instant,
    val paymentId: String,
)

data class StatementPage(
    val entries: List<StatementEntry>,
    val next: StatementCursor?,
)

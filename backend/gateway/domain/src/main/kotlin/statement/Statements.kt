package org.openfsp.gateway.statement

import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.protocol.Money
import java.time.Instant
import java.time.LocalDate

data class StatementEntry(
    val provider: String,
    val businessDate: LocalDate,
    val bookedAt: Instant,
    val amount: Money,
    val fee: ProviderFee?,
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

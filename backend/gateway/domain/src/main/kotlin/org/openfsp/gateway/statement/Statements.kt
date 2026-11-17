package org.openfsp.gateway.statement

import org.openfsp.gateway.payment.Payment
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import java.time.Instant
import java.time.LocalDate

enum class Match(
    val wire: String,
) {
    MATCHED("matched"),
    UNMATCHED("unmatched"),
    CONFLICT("conflict"),
    ;

    companion object {
        fun fromWire(value: String): Match? = entries.firstOrNull { it.wire == value }
    }
}

data class StatementEntry(
    val providerReference: String,
    val type: String,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val bookedAt: Instant,
    val providerOrderId: String?,
    val paymentId: String?,
    val match: Match,
)

data class OperatorStatement(
    val id: String,
    val provider: String,
    val businessDate: LocalDate,
    val complete: Boolean,
    val entries: List<StatementEntry>,
    val missingPayments: List<String>,
)

data class EntryCursor(
    val bookedAt: Instant,
    val position: Long,
)

data class EntryPage(
    val entries: List<Pair<Long, StatementEntry>>,
    val next: EntryCursor?,
)

data class StatusTotal(
    val count: Int,
    val amount: Money,
)

data class GatewayStatement(
    val provider: String,
    val businessDate: LocalDate,
    val final: Boolean,
    val payments: List<Payment>,
) {
    val totals: Map<PaymentStatus, StatusTotal>
        get() = payments.groupBy { it.status }.mapValues { (_, group) -> StatusTotal(group.size, Money(group.sumOf { it.amount.amount }, group.first().amount.currency)) }
}

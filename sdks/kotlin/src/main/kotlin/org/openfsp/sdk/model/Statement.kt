package org.openfsp.sdk.model

import java.time.Instant
import java.time.LocalDate

data class StatementEntry(
    val type: String,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val bookedAt: Instant,
    val paymentId: String?,
    val match: String,
)

data class Statement(
    val id: String,
    val provider: String,
    val businessDate: LocalDate,
    val complete: Boolean,
    val entries: List<StatementEntry>,
    val missingPayments: List<String>,
)

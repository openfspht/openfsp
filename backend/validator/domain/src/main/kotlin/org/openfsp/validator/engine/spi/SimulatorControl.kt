package org.openfsp.validator.engine.spi

import org.openfsp.validator.engine.JournalRecord
import java.time.Duration

data class SimulatedPaymentState(
    val paymentId: String,
    val settled: Boolean,
    val callbacksSent: Int,
)

data class JournalPage(
    val records: List<JournalRecord>,
    val totalPages: Int,
)

interface SimulatorControl {
    fun declareScenario(
        operator: String,
        order: String,
        scenario: String,
    )

    fun defaultScenario(
        operator: String,
        scenario: String,
    )

    fun payment(
        operator: String,
        order: String,
    ): SimulatedPaymentState?

    fun resolve(
        paymentId: String,
        outcome: String,
        reason: String?,
    )

    fun callback(paymentId: String)

    fun closeStatement(
        operator: String,
        day: String,
    )

    fun addStatementEntry(
        operator: String,
        day: String,
        amount: String,
        bookedAt: String,
    )

    fun reset()

    fun journal(
        operator: String,
        contains: String?,
        page: Int,
        size: Int,
    ): JournalPage
}

fun interface SimulatorControls {
    fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl
}

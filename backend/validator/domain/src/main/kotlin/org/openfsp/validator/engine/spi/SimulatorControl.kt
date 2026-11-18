package org.openfsp.validator.engine.spi

import java.time.Duration

data class SimulatedPaymentState(
    val paymentId: String,
    val settled: Boolean,
    val callbacksSent: Int,
)

interface SimulatorControl {
    fun declareScenario(
        provider: String,
        reference: String,
        scenario: String,
    )

    fun defaultScenario(
        provider: String,
        scenario: String,
    )

    fun payment(
        provider: String,
        reference: String,
    ): SimulatedPaymentState?

    fun resolve(
        paymentId: String,
        outcome: String,
        reason: String?,
    )

    fun callback(paymentId: String)

    fun reset()
}

fun interface SimulatorControls {
    fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl
}

package org.openfsp.validator.engine.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.spi.SimulatedPaymentState
import org.openfsp.validator.engine.spi.SimulatorControl
import org.openfsp.validator.engine.spi.SimulatorControls
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@Stub
class RecordingSimulatorControl :
    SimulatorControl,
    SimulatorControls {
    val calls = CopyOnWriteArrayList<String>()
    val payments = mutableMapOf<Pair<String, String>, SimulatedPaymentState>()
    val urls = CopyOnWriteArrayList<String>()

    override fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl = also { urls += baseUrl }

    override fun declareScenario(
        provider: String,
        reference: String,
        scenario: String,
    ) {
        calls += "declare $provider $reference $scenario"
    }

    override fun defaultScenario(
        provider: String,
        scenario: String,
    ) {
        calls += "default $provider $scenario"
    }

    override fun payment(
        provider: String,
        reference: String,
    ): SimulatedPaymentState? = payments[provider to reference]

    override fun resolve(
        paymentId: String,
        outcome: String,
        reason: String?,
    ) {
        calls += "resolve $paymentId $outcome"
    }

    override fun callback(paymentId: String) {
        calls += "callback $paymentId"
    }

    override fun closeStatement(
        provider: String,
        day: String,
    ) {
        calls += "close $provider $day"
    }

    override fun addStatementEntry(
        provider: String,
        day: String,
        amount: String,
        bookedAt: String,
    ) {
        calls += "entry $provider $day $amount"
    }

    override fun reset() {
        calls += "reset"
    }
}

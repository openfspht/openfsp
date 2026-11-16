package org.openfsp.validator.engine.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.JournalRecord
import org.openfsp.validator.engine.spi.JournalPage
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
    val journal = mutableMapOf<String, List<JournalRecord>>()
    val urls = CopyOnWriteArrayList<String>()

    override fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl = also { urls += baseUrl }

    override fun declareScenario(
        operator: String,
        order: String,
        scenario: String,
    ) {
        calls += "declare $operator $order $scenario"
    }

    override fun defaultScenario(
        operator: String,
        scenario: String,
    ) {
        calls += "default $operator $scenario"
    }

    override fun payment(
        operator: String,
        order: String,
    ): SimulatedPaymentState? = payments[operator to order]

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
        operator: String,
        day: String,
    ) {
        calls += "close $operator $day"
    }

    override fun addStatementEntry(
        operator: String,
        day: String,
        amount: String,
        bookedAt: String,
    ) {
        calls += "entry $operator $day $amount"
    }

    override fun reset() {
        calls += "reset"
    }

    override fun journal(
        operator: String,
        contains: String?,
        page: Int,
        size: Int,
    ): JournalPage {
        val records = journal[operator].orEmpty().chunked(size)
        return JournalPage(records.getOrNull(page).orEmpty(), records.size)
    }
}

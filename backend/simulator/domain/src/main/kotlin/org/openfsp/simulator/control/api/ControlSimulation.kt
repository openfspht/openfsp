package org.openfsp.simulator.control.api

import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.operator.Accounts
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import java.time.Instant

interface ControlSimulation {
    fun declare(
        operator: String,
        reference: String,
        scenario: Scenario,
    ): Boolean

    fun declareByReference(
        operator: String,
        reference: String,
        scenario: Scenario,
        account: String = Accounts.PUBLISHED,
    ): Declaration

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    ): Boolean

    fun scenariosFor(operator: String): List<Scenario>

    fun rotate(
        operator: String,
        enabled: Boolean,
    ): Boolean

    fun rotates(operator: String): Boolean

    fun defaultFor(operator: String): Scenario

    fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment?

    fun deliverCallback(paymentId: String): SimulatedPayment?

    fun payments(): List<SimulatedPayment>

    fun paymentByReference(
        operator: String,
        reference: String,
        account: String = Accounts.PUBLISHED,
    ): SimulatedPayment?

    fun journal(operator: String?): List<JournalEntry>

    fun record(entry: JournalEntry)

    fun reset()

    fun purge(before: Instant)
}

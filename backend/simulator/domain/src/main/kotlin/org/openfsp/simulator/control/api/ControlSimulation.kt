package org.openfsp.simulator.control.api

import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.control.Declaration
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.scenario.Scenario
import java.time.Instant

interface ControlSimulation {
    fun declare(
        operator: String,
        account: String,
        reference: String,
        scenario: Scenario,
    ): Boolean

    fun declareByReference(
        operator: String,
        reference: String,
        scenario: Scenario,
        account: String,
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
        scope: ControlScope,
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment?

    fun deliverCallback(
        scope: ControlScope,
        paymentId: String,
    ): SimulatedPayment?

    fun payments(scope: ControlScope): List<SimulatedPayment>

    fun paymentByReference(
        operator: String,
        reference: String,
        account: String,
    ): SimulatedPayment?

    fun journal(
        scope: ControlScope,
        operator: String?,
    ): List<JournalEntry>

    fun record(entry: JournalEntry)

    fun reset(scope: ControlScope)

    fun purge(before: Instant)
}

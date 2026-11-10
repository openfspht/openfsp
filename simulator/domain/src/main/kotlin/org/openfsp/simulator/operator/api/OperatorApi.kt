package org.openfsp.simulator.operator.api

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.OperatorPayment
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.OperatorStatement
import org.openfsp.simulator.operator.Outcome
import org.openfsp.simulator.operator.Scenario
import org.openfsp.simulator.operator.SimulatedPayment
import org.openfsp.simulator.operator.StatementDays
import org.openfsp.simulator.operator.StatementLine
import java.time.LocalDate

interface OperatePayments {
    fun create(
        operator: String,
        caller: Caller,
        request: CreatePaymentAtOperator,
    ): OperatorResponse<OperatorPayment>

    fun lookup(
        operator: String,
        caller: Caller,
        orderId: String,
    ): OperatorResponse<OperatorPayment>

    fun lookupPayment(
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment>

    fun statement(
        operator: String,
        caller: Caller,
        day: LocalDate,
    ): OperatorResponse<OperatorStatement>

    fun checkout(
        operator: String,
        paymentId: String,
    ): SimulatedPayment?

    fun pay(
        operator: String,
        paymentId: String,
        approved: Boolean,
    ): SimulatedPayment?

    fun profile(operator: String): OperatorProfile?
}

interface ControlSimulation {
    fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    )

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    )

    fun defaultFor(operator: String): Scenario

    fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment?

    fun deliverCallback(paymentId: String): SimulatedPayment?

    fun closeDay(
        operator: String,
        day: LocalDate,
    )

    fun statementDays(operator: String): StatementDays

    fun addEntry(
        operator: String,
        day: LocalDate,
        amount: String,
        bookedAt: String,
    )

    fun payments(): List<SimulatedPayment>

    fun journal(operator: String?): List<JournalEntry>

    fun record(entry: JournalEntry)

    fun reset()

    fun statementLines(
        operator: String,
        day: LocalDate,
    ): List<StatementLine>
}

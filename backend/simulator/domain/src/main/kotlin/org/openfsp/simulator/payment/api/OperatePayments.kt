package org.openfsp.simulator.payment.api

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.statement.OperatorStatement
import org.openfsp.simulator.statement.StatementEntries
import org.openfsp.simulator.statement.StatementMatch
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
        reference: String,
    ): OperatorResponse<OperatorPayment>

    fun lookupPayment(
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment>

    fun read(
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment>

    fun statement(
        operator: String,
        caller: Caller,
        day: LocalDate,
    ): OperatorResponse<OperatorStatement>

    fun statementAccess(
        operator: String,
        caller: Caller,
    ): OperatorResponse.Refused?

    fun statementEntries(
        operator: String,
        caller: Caller,
        match: StatementMatch,
        offset: Int,
        size: Int,
    ): OperatorResponse<StatementEntries>

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

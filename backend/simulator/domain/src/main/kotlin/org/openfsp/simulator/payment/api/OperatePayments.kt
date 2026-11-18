package org.openfsp.simulator.payment.api

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.StatementPage
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

    fun findByReference(
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
        from: LocalDate,
        to: LocalDate,
        offset: Int,
        size: Int,
    ): OperatorResponse<StatementPage>

    fun statementAccess(
        operator: String,
        caller: Caller,
    ): OperatorResponse.Refused?

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

package org.openfsp.simulator.operator.payment.api

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.operator.payment.OperatorPayment
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.statement.OperatorStatement
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

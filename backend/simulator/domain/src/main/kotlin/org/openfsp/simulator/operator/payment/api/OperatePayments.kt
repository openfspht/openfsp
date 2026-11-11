package org.openfsp.simulator.operator.payment.api

import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.operator.payment.OperatorPayment
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.space.SpaceId
import org.openfsp.simulator.operator.statement.OperatorStatement
import java.time.LocalDate

interface OperatePayments {
    fun create(
        space: SpaceId,
        operator: String,
        caller: Caller,
        request: CreatePaymentAtOperator,
    ): OperatorResponse<OperatorPayment>

    fun lookup(
        space: SpaceId,
        operator: String,
        caller: Caller,
        orderId: String,
    ): OperatorResponse<OperatorPayment>

    fun lookupPayment(
        space: SpaceId,
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment>

    fun statement(
        space: SpaceId,
        operator: String,
        caller: Caller,
        day: LocalDate,
    ): OperatorResponse<OperatorStatement>

    fun checkout(
        space: SpaceId,
        operator: String,
        paymentId: String,
    ): SimulatedPayment?

    fun pay(
        space: SpaceId,
        operator: String,
        paymentId: String,
        approved: Boolean,
    ): SimulatedPayment?

    fun profile(operator: String): OperatorProfile?
}

package org.openfsp.simulator.operator.payment

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.SimulationSettings
import org.openfsp.simulator.operator.payment.api.OperatePayments
import org.openfsp.simulator.operator.payment.spi.SimulatedPayments
import org.openfsp.simulator.operator.scenario.Scenario
import org.openfsp.simulator.operator.scenario.spi.ScenarioBook
import org.openfsp.simulator.operator.settlement.OperatorSettlement
import org.openfsp.simulator.operator.space.SpaceId
import org.openfsp.simulator.operator.spi.Delays
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.operator.spi.Sequences
import org.openfsp.simulator.operator.spi.SpaceAddresses
import org.openfsp.simulator.operator.statement.OperatorStatement
import org.openfsp.simulator.operator.statement.spi.StatementBook
import java.time.Duration
import java.time.LocalDate

@DomainService
class OperatorSimulation(
    private val profiles: OperatorProfiles,
    private val payments: SimulatedPayments,
    private val scenarios: ScenarioBook,
    private val statements: StatementBook,
    private val settlement: OperatorSettlement,
    private val sequences: Sequences,
    private val addresses: SpaceAddresses,
    private val delays: Delays,
    private val clock: SimulationClock,
    private val settings: SimulationSettings,
) : OperatePayments {
    override fun create(
        space: SpaceId,
        operator: String,
        caller: Caller,
        request: CreatePaymentAtOperator,
    ): OperatorResponse<OperatorPayment> {
        delays.pause(settings.responseDelay)
        val profile = profiles[operator] ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        val scenario = scenarios.scenarioFor(space, operator, request.orderId, profile.rotation)
        if (scenario == Scenario.CREDENTIAL_ECHO) return OperatorResponse.Refused(BAD_REQUEST, "BAD_REQUEST", "Rejected request authorized with ${caller.credential}")
        if (profile.idempotentByOrder) payments.withOrder(space, operator, request.orderId)?.let { return OperatorResponse.Answered(OK, presented(space, profile, it)) }
        if (scenario == Scenario.PROVIDER_ERROR) return OperatorResponse.Refused(SERVER_ERROR, "INTERNAL", "Simulated provider error")

        val now = clock.now()
        val payment =
            payments.save(
                space,
                SimulatedPayment(
                    operator = operator,
                    paymentId = "%s-%08d".format(profile.referencePrefix, sequences.next(space, PAYMENTS)),
                    orderId = request.orderId,
                    amount = request.amount,
                    msisdn = request.msisdn,
                    callbackUrl = request.callbackUrl.takeIf { profile.callbacks },
                    scenario = scenario,
                    createdAt = now,
                    expiresAt = now.plus(if (scenario == Scenario.EXPIRE) SHORT_WINDOW else PAYMENT_WINDOW),
                    description = request.description,
                    locale = request.locale,
                    returnUrl = request.returnUrl,
                ),
            )
        settlement.schedule(space, payment)
        if (scenario.hangs) delays.pause(settings.hangDuration)
        if (scenario == Scenario.TIMEOUT_THEN_NOTHING) payments.forget(space, payment.paymentId)
        return OperatorResponse.Answered(CREATED, presented(space, profile, payment))
    }

    override fun lookup(
        space: SpaceId,
        operator: String,
        caller: Caller,
        orderId: String,
    ): OperatorResponse<OperatorPayment> = lookedUp(space, operator, caller) { payments.withOrder(space, operator, orderId) }

    override fun lookupPayment(
        space: SpaceId,
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment> = lookedUp(space, operator, caller) { payments.find(space, paymentId)?.takeIf { it.operator == operator } }

    override fun statement(
        space: SpaceId,
        operator: String,
        caller: Caller,
        day: LocalDate,
    ): OperatorResponse<OperatorStatement> {
        delays.pause(settings.responseDelay)
        profiles[operator]?.takeIf { it.statements } ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        return OperatorResponse.Answered(OK, OperatorStatement(day, statements.isClosed(space, operator, day), statements.lines(space, operator, day)))
    }

    override fun checkout(
        space: SpaceId,
        operator: String,
        paymentId: String,
    ): SimulatedPayment? = payments.find(space, paymentId)?.takeIf { it.operator == operator }

    override fun pay(
        space: SpaceId,
        operator: String,
        paymentId: String,
        approved: Boolean,
    ): SimulatedPayment? {
        checkout(space, operator, paymentId) ?: return null
        return if (approved) {
            settlement.resolve(space, paymentId, Outcome.SUCCESSFUL, null)
        } else {
            settlement.resolve(space, paymentId, Outcome.FAILED, "PAYER_CANCELED")
        }
    }

    override fun profile(operator: String): OperatorProfile? = profiles[operator]

    private fun lookedUp(
        space: SpaceId,
        operator: String,
        caller: Caller,
        find: () -> SimulatedPayment?,
    ): OperatorResponse<OperatorPayment> {
        delays.pause(settings.responseDelay)
        profiles[operator]?.takeIf { it.lookup } ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        val payment = find() ?: return OperatorResponse.Refused(NOT_FOUND, "NOT_FOUND", "Unknown order")
        val looked = payments.update(space, payment.paymentId) { it.copy(lookups = it.lookups + 1) }!!
        return OperatorResponse.Answered(OK, settlement.expireIfDue(space, looked).asLookedUp())
    }

    private fun presented(
        space: SpaceId,
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): OperatorPayment {
        val reported = payment.asReported()
        return if (profile.hostsCheckoutFor(payment)) reported.copy(checkoutUrl = "${addresses.publicBaseUrl(space)}/${profile.id}/checkout/${payment.paymentId}") else reported
    }

    private fun unknown() = OperatorResponse.Refused(NOT_FOUND, "NOT_FOUND", "Unknown endpoint")

    private fun unauthorized() = OperatorResponse.Refused(UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials")

    private companion object {
        const val PAYMENTS = "payments"
        const val OK = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
        const val SERVER_ERROR = 500
        val PAYMENT_WINDOW: Duration = Duration.ofMinutes(15)
        val SHORT_WINDOW: Duration = Duration.ofMinutes(1)
    }
}

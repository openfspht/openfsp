package org.openfsp.simulator.payment

import org.openfsp.ddd.DomainService
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.common.spi.Delays
import org.openfsp.simulator.common.spi.PaymentIds
import org.openfsp.simulator.common.spi.PublicAddress
import org.openfsp.simulator.common.spi.Sequences
import org.openfsp.simulator.operator.AmountLimits
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.api.OperatePayments
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.scenario.spi.ScenarioBook
import org.openfsp.simulator.settlement.OperatorSettlement
import java.time.Duration
import java.time.LocalDate

@DomainService
class OperatorSimulation(
    private val profiles: OperatorProfiles,
    private val payments: SimulatedPayments,
    private val scenarios: ScenarioBook,
    private val settlement: OperatorSettlement,
    private val sequences: Sequences,
    private val ids: PaymentIds,
    private val address: PublicAddress,
    private val delays: Delays,
    private val clock: SimulationClock,
    private val settings: SimulationSettings,
) : OperatePayments {
    override fun create(
        operator: String,
        caller: Caller,
        request: CreatePaymentAtOperator,
    ): OperatorResponse<OperatorPayment> {
        delays.pause(settings.responseDelay)
        val profile = profiles[operator] ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        if (profile.limits?.admits(request.amount) == false) return OperatorResponse.Refused(UNPROCESSABLE, AmountLimits.OUT_OF_RANGE, "The amount is outside the announced bounds")
        val scenario = scenarios.scenarioFor(operator, request.reference, profile.rotation)
        if (scenario == Scenario.CREDENTIAL_ECHO) return OperatorResponse.Refused(BAD_REQUEST, "BAD_REQUEST", "Rejected request authorized with ${caller.credential}")
        if (profile.idempotentByReference) payments.withReference(operator, request.reference)?.let { return OperatorResponse.Answered(OK, presented(profile, it)) }
        if (scenario == Scenario.PROVIDER_ERROR) return OperatorResponse.Refused(SERVER_ERROR, "INTERNAL", "Simulated provider error")

        val now = clock.now()
        val candidate =
            SimulatedPayment(
                operator = operator,
                paymentId = if (profile.dialect == Dialect.OPENFSP) ids.opaque() else "%s-%08d".format(profile.referencePrefix, sequences.next(PAYMENTS)),
                reference = request.reference,
                amount = request.amount,
                msisdn = request.msisdn,
                callbackUrl = request.callbackUrl.takeIf { profile.callbacks } ?: profile.webhookUrl,
                scenario = scenario,
                createdAt = now,
                expiresAt = now.plus(if (scenario == Scenario.EXPIRE) SHORT_WINDOW else PAYMENT_WINDOW),
                description = request.description,
                metadata = request.metadata,
                locale = request.locale,
                returnUrl = request.returnUrl,
            )
        val payment = if (profile.idempotentByReference) payments.saveIfAbsent(candidate) else payments.save(candidate)
        if (payment.paymentId != candidate.paymentId) return OperatorResponse.Answered(OK, presented(profile, payment))
        settlement.schedule(payment)
        if (scenario.hangs) delays.pause(settings.hangDuration)
        if (scenario == Scenario.TIMEOUT_THEN_NOTHING) payments.forget(payment.paymentId)
        return OperatorResponse.Answered(CREATED, presented(profile, payment))
    }

    override fun lookup(
        operator: String,
        caller: Caller,
        reference: String,
    ): OperatorResponse<OperatorPayment> = lookedUp(operator, caller) { payments.withReference(operator, reference) }

    override fun lookupPayment(
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment> = lookedUp(operator, caller) { payments.find(paymentId)?.takeIf { it.operator == operator } }

    override fun read(
        operator: String,
        caller: Caller,
        paymentId: String,
    ): OperatorResponse<OperatorPayment> {
        delays.pause(settings.responseDelay)
        profiles[operator] ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        val payment = checkout(operator, paymentId) ?: return OperatorResponse.Refused(NOT_FOUND, "NOT_FOUND", "Unknown payment")
        return OperatorResponse.Answered(OK, settlement.expireIfDue(payment).asReported())
    }

    override fun statement(
        operator: String,
        caller: Caller,
        from: LocalDate,
        to: LocalDate,
        offset: Int,
        size: Int,
    ): OperatorResponse<StatementPage> {
        delays.pause(settings.responseDelay)
        statementAccess(operator, caller)?.let { return it }
        if (profiles[operator]?.statements != true) return OperatorResponse.Answered(OK, StatementPage(emptyList(), null))
        val succeeded =
            payments
                .all()
                .filter { it.operator == operator && it.outcome == Outcome.SUCCESSFUL && it.settledAt?.let { settled -> clock.businessDate(settled) in from..to } == true }
                .sortedWith(compareBy({ it.settledAt }, { it.paymentId }))
        val next = (offset + size).takeIf { it < succeeded.size }
        return OperatorResponse.Answered(OK, StatementPage(succeeded.drop(offset).take(size), next))
    }

    override fun statementAccess(
        operator: String,
        caller: Caller,
    ): OperatorResponse.Refused? =
        when {
            profiles[operator] == null -> unknown()
            !caller.authenticated -> unauthorized()
            !caller.principal.readsStatements -> OperatorResponse.Refused(FORBIDDEN, "FORBIDDEN", "The key lacks the statements:read scope")
            else -> null
        }

    override fun checkout(
        operator: String,
        paymentId: String,
    ): SimulatedPayment? = payments.find(paymentId)?.takeIf { it.operator == operator }

    override fun pay(
        operator: String,
        paymentId: String,
        approved: Boolean,
    ): SimulatedPayment? {
        checkout(operator, paymentId) ?: return null
        return if (approved) {
            settlement.resolve(paymentId, Outcome.SUCCESSFUL, null)
        } else {
            settlement.resolve(paymentId, Outcome.FAILED, "PAYER_CANCELED")
        }
    }

    override fun profile(operator: String): OperatorProfile? = profiles[operator]

    private fun lookedUp(
        operator: String,
        caller: Caller,
        find: () -> SimulatedPayment?,
    ): OperatorResponse<OperatorPayment> {
        delays.pause(settings.responseDelay)
        profiles[operator]?.takeIf { it.lookup } ?: return unknown()
        if (!caller.authenticated) return unauthorized()
        val payment = find() ?: return OperatorResponse.Refused(NOT_FOUND, "NOT_FOUND", "Unknown order")
        val looked = payments.update(payment.paymentId) { it.copy(lookups = it.lookups + 1) }!!
        return OperatorResponse.Answered(OK, settlement.expireIfDue(looked).asLookedUp())
    }

    private fun presented(
        profile: OperatorProfile,
        payment: SimulatedPayment,
    ): OperatorPayment {
        val reported = payment.asReported()
        return if (profile.hostsCheckoutFor(payment)) reported.copy(checkoutUrl = "${address.baseUrl()}/${profile.id}/checkout/${payment.paymentId}") else reported
    }

    private fun unknown() = OperatorResponse.Refused(NOT_FOUND, "NOT_FOUND", "Unknown endpoint")

    private fun unauthorized() = OperatorResponse.Refused(UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials")

    private companion object {
        const val PAYMENTS = "payments"
        const val OK = 200
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val UNPROCESSABLE = 422
        const val SERVER_ERROR = 500
        val PAYMENT_WINDOW: Duration = Duration.ofMinutes(15)
        val SHORT_WINDOW: Duration = Duration.ofMinutes(1)
    }
}

package org.openfsp.gateway.conformance

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.conformance.api.ControlConformance
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.api.AttestPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.text.Normalizer

@DomainService
class ConformanceControl(
    private val scripts: PaymentScripts,
    private val payments: Payments,
    private val providers: Providers,
    private val attestation: AttestPayment,
) : ControlConformance {
    override fun declare(
        principal: String,
        reference: String,
        scenario: String?,
    ) {
        val run = runOf(principal)
        val chosen = scenario?.let(Scenario::named) ?: throw OpenFspException.invalid(SCENARIO, "is not a scenario of the control interface")
        if (chosen.channel != null && providers.all().none { chosen.isPlayableOn(it.descriptor) }) {
            throw OpenFspException.invalid(SCENARIO, "no payment service of this gateway can play ${chosen.name}")
        }
        scripts.declare(run, normalized(reference), chosen)
    }

    override fun state(
        principal: String,
        reference: String,
    ): ControlledPayment {
        val run = runOf(principal)
        val payment = payments.withReference(normalized(reference))?.takeIf { it.isVisible && it.belongsTo(run) } ?: throw OpenFspException.notFound()
        val script = scripts.of(run, payment)
        return ControlledPayment(payment.id, script?.isSettled ?: payment.isTerminal, script?.callbacksSent ?: 0)
    }

    override fun resolve(
        principal: String,
        paymentId: String,
        outcome: String?,
        reason: String?,
    ) {
        val (payment, script) = scriptedPayment(principal, paymentId)
        val status = OUTCOMES.firstOrNull { it.wire == outcome } ?: throw OpenFspException.invalid(OUTCOME, "must be one of ${OUTCOMES.joinToString { it.wire }}")
        val failure = reason?.let { given -> FailureReason.entries.firstOrNull { it.wire == given } ?: throw OpenFspException.invalid(REASON, "is not a failure reason") }
        val resolved = scripts.resolve(script.order, ScriptedOutcome(status, (failure ?: FailureReason.UNSPECIFIED).takeIf { status == PaymentStatus.FAILED })) ?: return
        val reached = resolved.outcome ?: return
        if (!resolved.isObservable && payments.find(payment.id)?.isTerminal == false) {
            attestation.attest(payment.id, Attestation(ATTESTING, reached.status, EVIDENCE, reached.failureReason))
        }
    }

    override fun callback(
        principal: String,
        paymentId: String,
    ) = scripts.callBack(scriptedPayment(principal, paymentId).second.order)

    private fun scriptedPayment(
        principal: String,
        paymentId: String,
    ): Pair<Payment, ScriptedPayment> {
        val run = runOf(principal)
        val payment = payments.find(paymentId)?.takeIf { it.belongsTo(run) } ?: throw OpenFspException.notFound()
        val script =
            scripts.of(run, payment)?.takeIf { it.accepted }
                ?: throw OpenFspException(ProblemType.STATE_CONFLICT, "The payment follows no scenario of this run at its payment service")
        return payment to script
    }

    private fun runOf(principal: String): ConformanceRun =
        scripts.runOf(principal) ?: throw OpenFspException(ProblemType.FORBIDDEN, "The key is not the api_key of a conformance preparation in progress")

    private fun Payment.belongsTo(run: ConformanceRun): Boolean = creationRequest?.principal in run.principals

    private fun normalized(reference: String): String = Normalizer.normalize(reference, Normalizer.Form.NFC)

    private companion object {
        const val SCENARIO = "scenario"
        const val OUTCOME = "outcome"
        const val REASON = "reason"
        const val ATTESTING = "conformance control interface"
        const val EVIDENCE = "outcome given by the suite through the resolve operation"
        val OUTCOMES = listOf(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.EXPIRED)
    }
}

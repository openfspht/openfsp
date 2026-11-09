package org.openfsp.simulator.operator

import org.openfsp.simulator.Outcome
import org.openfsp.simulator.Scenario
import org.openfsp.simulator.SimulatedPayment
import org.openfsp.simulator.SimulatorProperties
import org.openfsp.simulator.SimulatorState
import org.openfsp.simulator.StatementLine
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.HexFormat
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class OperatorCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
)

@Component
class Settlement(
    private val state: SimulatorState,
    private val properties: SimulatorProperties,
    private val scheduler: ScheduledExecutorService,
    private val client: RestClient,
    private val json: JsonMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun schedule(payment: SimulatedPayment) {
        val (outcome, reason) =
            when (payment.scenario) {
                Scenario.SUCCESS, Scenario.NO_CALLBACK, Scenario.DUPLICATE_CALLBACK, Scenario.AMOUNT_MISMATCH, Scenario.TIMEOUT,
                Scenario.TIMEOUT_THEN_SUCCESS, Scenario.LATE_CALLBACK, Scenario.UNKNOWN_STATUS, Scenario.CONTRADICT,
                ->
                    Outcome.SUCCESSFUL to null
                Scenario.DECLINE -> Outcome.FAILED to "DECLINED"
                Scenario.INSUFFICIENT -> Outcome.FAILED to "INSUFFICIENT_FUNDS"
                Scenario.PENDING_FOREVER, Scenario.EXPIRE, Scenario.PROVIDER_ERROR, Scenario.TIMEOUT_THEN_NOTHING, Scenario.CREDENTIAL_ECHO -> return
            }
        scheduler.schedule({ resolve(payment.paymentId, outcome, reason) }, properties.settleAfter.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun resolve(
        paymentId: String,
        outcome: Outcome,
        reason: String?,
    ): SimulatedPayment? {
        val resolved =
            state.update(paymentId) { current ->
                if (current.outcome != null) {
                    current
                } else {
                    current.copy(
                        outcome = outcome,
                        reason = reason,
                        reportedAmount = if (current.scenario == Scenario.AMOUNT_MISMATCH) shifted(current.amount) else null,
                    )
                }
            } ?: return null
        if (resolved.outcome == Outcome.SUCCESSFUL && properties.operators[resolved.operator]?.statements == true) {
            state.book(
                resolved.operator,
                StatementLine(resolved.paymentId, resolved.orderId, "PAYMENT", resolved.reportedAmount ?: resolved.amount, LOCAL.format(state.localNow())),
            )
        }
        when (resolved.scenario) {
            Scenario.NO_CALLBACK, Scenario.UNKNOWN_STATUS -> Unit
            Scenario.DUPLICATE_CALLBACK -> repeat(DUPLICATES) { sendCallback(resolved) }
            Scenario.LATE_CALLBACK ->
                scheduler.schedule({ sendCallback(resolved) }, properties.lateCallbackDelay.toMillis(), TimeUnit.MILLISECONDS)
            else -> sendCallback(resolved)
        }
        return resolved
    }

    fun expireIfDue(payment: SimulatedPayment): SimulatedPayment =
        if (payment.outcome == null && state.now().isAfter(payment.expiresAt)) {
            resolve(payment.paymentId, Outcome.EXPIRED, null) ?: payment
        } else {
            payment
        }

    fun sendCallback(payment: SimulatedPayment) {
        val url = payment.callbackUrl ?: return
        val outcome = payment.outcome ?: return
        val body =
            json.writeValueAsBytes(
                OperatorCallback(payment.paymentId, payment.orderId, outcome.wire, payment.reportedAmount ?: payment.amount, payment.reason),
            )
        runCatching {
            client
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { headers ->
                    if (properties.operators[payment.operator]?.callbackSecret != null) {
                        headers.set(SIGNATURE_HEADER, "sha256=${sign(payment.operator, body)}")
                    }
                }
                .body(body)
                .retrieve()
                .toBodilessEntity()
        }.onSuccess { state.update(payment.paymentId) { it.copy(callbacksSent = it.callbacksSent + 1) } }
            .onFailure { log.warn("Callback for {} to {} failed: {}", payment.paymentId, url, it.message) }
    }

    private fun sign(
        operator: String,
        body: ByteArray,
    ): String {
        val secret = properties.operators.getValue(operator).callbackSecret ?: error("$operator does not sign callbacks")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(body))
    }

    private fun shifted(amount: String): String = BigDecimal(amount).subtract(BigDecimal.ONE).toPlainString()

    companion object {
        const val SIGNATURE_HEADER = "X-Mock-Signature"
        private const val DUPLICATES = 3
        val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}

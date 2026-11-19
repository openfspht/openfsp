package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.Pause
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonBoolean
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.at
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.suite.ControlAction
import java.net.URLEncoder
import java.time.Clock
import java.time.Duration

class PreparationException(
    message: String,
) : RuntimeException(message)

class UnavailableScenarioException(
    message: String,
) : RuntimeException(message)

data class ControlledPayment(
    val paymentId: String,
    val settled: Boolean,
    val callbacksSent: Int,
)

class OutcomeControl(
    private val endpoint: String?,
    private val apiKey: String,
    private val http: Transport,
    private val json: JsonCodec,
    private val clock: Clock,
    private val pause: Pause,
) {
    fun perform(
        action: ControlAction,
        variables: Variables,
    ) {
        action.declare?.let { declare(variables.resolve(it.reference), variables.resolve(it.scenario)) }
        action.resolve?.let {
            val members = listOfNotNull(OUTCOME to variables.resolve(it.outcome), it.reason?.let { reason -> REASON to variables.resolve(reason) })
            send(POST, "/payments/${segment(paymentOf(variables.resolve(it.reference)))}/resolve", members.toMap())
        }
        action.callback?.let { send(POST, "/payments/${segment(paymentOf(variables.resolve(it.reference)))}/callback", null) }
        action.settled?.let { settled(variables.resolve(it.reference), it.callbacks, Durations.parse(it.within), Durations.parse(it.every)) }
    }

    fun payment(reference: String): ControlledPayment? {
        val path = "/references/${segment(reference)}"
        val exchange = exchange(GET, path, null)
        if (exchange.status == NOT_FOUND) return null
        if (exchange.status != OK) throw refused(GET, path, exchange)
        val document = runCatching { exchange.json(json) }.getOrNull()
        val paymentId = document?.at(PAYMENT)?.string
        val settled = (document?.at(SETTLED) as? JsonBoolean)?.value
        if (paymentId == null || settled == null) throw PreparationException("the control interface answered GET $path without the payment and settled members")
        return ControlledPayment(paymentId, settled, (document.at(CALLBACKS_SENT) as? JsonNumber)?.value?.toInt() ?: 0)
    }

    private fun declare(
        reference: String,
        scenario: String,
    ) {
        val path = "/references/${segment(reference)}/scenario"
        val exchange = exchange(PUT, path, mapOf(SCENARIO to scenario))
        if (exchange.status == UNPROCESSABLE) throw UnavailableScenarioException("The implementation refused the scenario $scenario for a payment service that declares the capabilities it exercises (conformance 4.9.6).")
        if (exchange.status !in SUCCESS) throw refused(PUT, path, exchange)
    }

    private fun settled(
        reference: String,
        callbacks: Int,
        within: Duration,
        every: Duration,
    ) {
        val deadline = clock.instant().plus(within)
        while (true) {
            val payment = payment(reference)
            if (payment != null && payment.settled && payment.callbacksSent >= callbacks) return
            if (clock.instant().plus(every).isAfter(deadline)) {
                throw PreparationException("the implementation did not settle the payment with reference $reference${if (callbacks > 0) " with $callbacks event(s)" else ""} within ${within.toSeconds()}s")
            }
            pause.pause(every)
        }
    }

    private fun paymentOf(reference: String): String = payment(reference)?.paymentId ?: throw PreparationException("the control interface knows no payment with reference $reference")

    private fun send(
        method: String,
        path: String,
        body: Map<String, String>?,
    ) {
        val exchange = exchange(method, path, body)
        if (exchange.status !in SUCCESS) throw refused(method, path, exchange)
    }

    private fun exchange(
        method: String,
        path: String,
        body: Map<String, String>?,
    ): Exchange {
        val base = endpoint ?: throw PreparationException("the scenario chooses an outcome but the implementation offers no control interface")
        val payload = body?.let { json.write(JsonObject(it.mapValues { (_, value) -> JsonString(value) as Json })) }
        val headers = mapOf(AUTHORIZATION to "$BEARER$apiKey", ACCEPT to Expectations.JSON) + if (payload != null) mapOf(Expectations.CONTENT_TYPE to Expectations.JSON) else emptyMap()
        return try {
            http.send(OutgoingRequest(method, base + path, headers, payload))
        } catch (exception: TransportException) {
            throw PreparationException("the control interface is unreachable: ${exception.message}")
        }
    }

    private fun refused(
        method: String,
        path: String,
        exchange: Exchange,
    ) = PreparationException("the control interface refused $method $path with ${exchange.status} ${exchange.text}".trim())

    private fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private companion object {
        const val GET = "GET"
        const val PUT = "PUT"
        const val POST = "POST"
        const val ACCEPT = "Accept"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val SCENARIO = "scenario"
        const val OUTCOME = "outcome"
        const val REASON = "reason"
        const val PAYMENT = "/payment"
        const val SETTLED = "/settled"
        const val CALLBACKS_SENT = "/callbacks_sent"
        const val OK = 200
        const val NOT_FOUND = 404
        const val UNPROCESSABLE = 422
        val SUCCESS = 200..299
    }
}

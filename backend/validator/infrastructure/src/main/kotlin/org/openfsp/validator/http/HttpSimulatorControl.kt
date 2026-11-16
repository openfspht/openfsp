package org.openfsp.validator.http

import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.Expectations
import org.openfsp.validator.engine.JournalRecord
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.PreparationException
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.engine.spi.JournalPage
import org.openfsp.validator.engine.spi.SimulatedPaymentState
import org.openfsp.validator.engine.spi.SimulatorControl
import org.openfsp.validator.engine.spi.SimulatorControls
import org.openfsp.validator.engine.spi.Transport
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLEncoder
import java.time.Duration

class HttpSimulatorControls : SimulatorControls {
    override fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl = HttpSimulatorControl(baseUrl, JdkTransport(timeout))
}

class HttpSimulatorControl(
    private val baseUrl: String,
    private val http: Transport,
    private val json: JsonMapper = JsonMapper.builder().build(),
) : SimulatorControl {
    override fun declareScenario(
        operator: String,
        order: String,
        scenario: String,
    ) = put("/control/operators/${segment(operator)}/orders/${segment(order)}/scenario", mapOf("scenario" to scenario))

    override fun defaultScenario(
        operator: String,
        scenario: String,
    ) = put("/control/operators/${segment(operator)}/default-scenario", mapOf("scenario" to scenario))

    override fun payment(
        operator: String,
        order: String,
    ): SimulatedPaymentState? =
        get("/control/payments?operator=${encode(operator)}&q=${encode(order)}&size=$PAGE_SIZE")
            .get("data")
            ?.firstOrNull { it.path("order_id").asString() == order }
            ?.let { payment ->
                val outcome = payment.path("outcome")
                SimulatedPaymentState(payment.path("payment_id").asString(), !outcome.isNull && !outcome.isMissingNode, payment.path("callbacks_sent").asInt(0))
            }

    override fun resolve(
        paymentId: String,
        outcome: String,
        reason: String?,
    ) = post("/control/payments/${segment(paymentId)}/resolve", mapOf("outcome" to outcome, "reason" to reason))

    override fun callback(paymentId: String) = post("/control/payments/${segment(paymentId)}/callback", null)

    override fun closeStatement(
        operator: String,
        day: String,
    ) = post("/control/operators/${segment(operator)}/statements/${segment(day)}/close", null)

    override fun addStatementEntry(
        operator: String,
        day: String,
        amount: String,
        bookedAt: String,
    ) = post("/control/operators/${segment(operator)}/statements/${segment(day)}/entries", mapOf("amount" to amount, "booked_at" to bookedAt))

    override fun reset() = post("/control/reset", null)

    override fun journal(
        operator: String,
        contains: String?,
        page: Int,
        size: Int,
    ): JournalPage {
        val query =
            buildString {
                append("/control/journal?operator=").append(encode(operator)).append("&size=").append(size).append("&page=").append(page)
                contains?.let { append("&q=").append(encode(it)) }
            }
        val document = get(query)
        val records = mutableListOf<JournalRecord>()
        document.get("data")?.forEach { entry ->
            records += JournalRecord(entry.path("method").asString(), entry.path("path").asString(), entry.path("body").asString(""), entry.path("status").asInt(0), entry.path("response_body").asString(""))
        }
        return JournalPage(records, document.path("page").path("total_pages").asInt(0))
    }

    private fun get(path: String): JsonNode {
        val exchange = send("GET", path, null)
        return exchange.json(json) ?: throw PreparationException("the simulator answered GET $path with an unreadable body")
    }

    private fun put(
        path: String,
        body: Map<String, String?>,
    ) {
        send("PUT", path, body)
    }

    private fun post(
        path: String,
        body: Map<String, String?>?,
    ) {
        send("POST", path, body)
    }

    private fun send(
        method: String,
        path: String,
        body: Map<String, String?>?,
    ): Exchange {
        val payload = body?.filterValues { it != null }?.let { json.writeValueAsBytes(it) }
        val headers = if (payload != null) mapOf(Expectations.CONTENT_TYPE to JSON) else emptyMap()
        val exchange =
            try {
                http.send(OutgoingRequest(method, baseUrl.trimEnd('/') + path, headers, payload))
            } catch (exception: TransportException) {
                throw PreparationException("the simulator is unreachable: ${exception.message}")
            }
        if (exchange.status !in SUCCESS) throw PreparationException("the simulator refused $method $path with ${exchange.status} ${exchange.text}".trim())
        return exchange
    }

    private fun segment(value: String): String = encode(value).replace("+", "%20")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        const val JSON = "application/json"
        const val PAGE_SIZE = 100
        val SUCCESS = 200..299
    }
}

package org.openfsp.validator.engine

import org.openfsp.validator.suite.JournalAssertion
import org.openfsp.validator.suite.SimulatorAction
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

class PreparationException(
    message: String,
) : RuntimeException(message)

data class JournalRecord(
    val method: String,
    val path: String,
    val body: String = "",
    val status: Int = 0,
    val responseBody: String = "",
)

class Simulator(
    private val baseUrl: String?,
    private val http: Transport,
    private val json: JsonMapper,
) {
    fun perform(
        action: SimulatorAction,
        variables: Variables,
    ) {
        action.declare?.let { put("/control/operators/${segment(variables.resolve(it.operator))}/orders/${segment(variables.resolve(it.order))}/scenario", mapOf("scenario" to variables.resolve(it.scenario))) }
        action.default?.let { put("/control/operators/${segment(variables.resolve(it.operator))}/default-scenario", mapOf("scenario" to variables.resolve(it.scenario))) }
        action.resolve?.let {
            val paymentId = paymentOf(variables.resolve(it.operator), variables.resolve(it.order))
            post("/control/payments/${segment(paymentId)}/resolve", mapOf("outcome" to variables.resolve(it.outcome), "reason" to it.reason?.let(variables::resolve)))
        }
        action.callback?.let {
            val paymentId = paymentOf(variables.resolve(it.operator), variables.resolve(it.order))
            post("/control/payments/${segment(paymentId)}/callback", null)
        }
        action.closeStatement?.let { post("/control/operators/${segment(variables.resolve(it.operator))}/statements/${segment(variables.resolve(it.day))}/close", null) }
        action.statementEntry?.let {
            post(
                "/control/operators/${segment(variables.resolve(it.operator))}/statements/${segment(variables.resolve(it.day))}/entries",
                mapOf("amount" to variables.resolve(it.amount), "booked_at" to variables.resolve(it.bookedAt)),
            )
        }
        action.settled?.let { settled(variables.resolve(it.operator), variables.resolve(it.order), it.callbacks, Durations.parse(it.within), Durations.parse(it.every)) }
        if (action.reset) post("/control/reset", null)
    }

    fun journal(
        assertion: JournalAssertion,
        variables: Variables,
    ): List<JournalRecord> {
        val operator = variables.resolve(assertion.operator)
        val contains = assertion.contains?.let(variables::resolve)
        val records = mutableListOf<JournalRecord>()
        var page = 0
        do {
            val query = buildString {
                append("/control/journal?operator=").append(encode(operator)).append("&size=").append(PAGE_SIZE).append("&page=").append(page)
                contains?.let { append("&q=").append(encode(it)) }
            }
            val document = get(query)
            document.get("data")?.forEach { entry ->
                records += JournalRecord(entry.path("method").asString(), entry.path("path").asString(), entry.path("body").asString(""), entry.path("status").asInt(0), entry.path("response_body").asString(""))
            }
            val totalPages = document.path("page").path("total_pages").asInt(0)
            page++
        } while (page < totalPages && page < MAX_PAGES)
        return records
    }

    private fun settled(
        operator: String,
        order: String,
        callbacks: Int,
        within: Duration,
        every: Duration,
    ) {
        val deadline = Instant.now().plus(within)
        while (true) {
            val payment = paymentRecord(operator, order)
            if (payment != null && !payment.path("outcome").isNull && !payment.path("outcome").isMissingNode && payment.path("callbacks_sent").asInt(0) >= callbacks) return
            if (Instant.now().plus(every).isAfter(deadline)) {
                throw PreparationException("the simulator did not settle the $operator payment for order $order${if (callbacks > 0) " with $callbacks callback(s)" else ""} within ${within.toSeconds()}s")
            }
            Thread.sleep(every)
        }
    }

    private fun paymentRecord(
        operator: String,
        order: String,
    ): JsonNode? = get("/control/payments?operator=${encode(operator)}&q=${encode(order)}&size=$PAGE_SIZE").get("data")?.firstOrNull { it.path("order_id").asString() == order }

    private fun paymentOf(
        operator: String,
        order: String,
    ): String {
        return paymentRecord(operator, order)?.path("payment_id")?.asString()
            ?: throw PreparationException("the simulator holds no $operator payment for order $order")
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
        val base = baseUrl ?: throw PreparationException("the scenario drives the simulator but no simulator control URL is configured")
        val payload = body?.filterValues { it != null }?.let { json.writeValueAsBytes(it) }
        val headers = if (payload != null) mapOf(Expectations.CONTENT_TYPE to JSON) else emptyMap()
        val exchange =
            try {
                http.send(OutgoingRequest(method, base.trimEnd('/') + path, headers, payload))
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
        const val MAX_PAGES = 50
        val SUCCESS = 200..299
    }
}

package org.openfsp.validator.engine

import org.openfsp.validator.suite.JournalAssertion
import org.openfsp.validator.suite.SimulatorAction
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLEncoder

class PreparationException(
    message: String,
) : RuntimeException(message)

data class JournalRecord(
    val method: String,
    val path: String,
)

class Simulator(
    private val baseUrl: String?,
    private val http: Http,
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
        if (action.reset) post("/control/reset", null)
    }

    fun journal(
        assertion: JournalAssertion,
        variables: Variables,
    ): List<JournalRecord> {
        val operator = variables.resolve(assertion.operator)
        val contains = assertion.contains?.let(variables::resolve)
        val path = assertion.path?.let { Regex(variables.resolve(it)) }
        val method = assertion.method?.let(variables::resolve)
        val records = mutableListOf<JournalRecord>()
        var page = 0
        do {
            val query = buildString {
                append("/control/journal?operator=").append(encode(operator)).append("&size=").append(PAGE_SIZE).append("&page=").append(page)
                contains?.let { append("&q=").append(encode(it)) }
            }
            val document = get(query)
            document.get("data")?.forEach { entry ->
                records += JournalRecord(entry.path("method").asString(), entry.path("path").asString())
            }
            val totalPages = document.path("page").path("total_pages").asInt(0)
            page++
        } while (page < totalPages && page < MAX_PAGES)
        return records.filter { (method == null || it.method.equals(method, ignoreCase = true)) && (path == null || path.matches(it.path)) }
    }

    private fun paymentOf(
        operator: String,
        order: String,
    ): String {
        val document = get("/control/payments?operator=${encode(operator)}&q=${encode(order)}&size=$PAGE_SIZE")
        return document.get("data")?.firstOrNull { it.path("order_id").asString() == order }?.path("payment_id")?.asString()
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

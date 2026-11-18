package org.openfsp.validator.runs.http

import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.Expectations
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.PreparationException
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.engine.spi.SimulatedPaymentState
import org.openfsp.validator.engine.spi.SimulatorControl
import org.openfsp.validator.engine.spi.SimulatorControls
import org.openfsp.validator.engine.spi.Transport
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue
import java.net.URLEncoder
import java.time.Duration

data class SimulatedPaymentResource(
    val paymentId: String,
    val orderId: String? = null,
    val reference: String? = null,
    val outcome: String? = null,
    val settled: Boolean? = null,
    val callbacksSent: Int = 0,
) {
    val merchantReference: String? get() = reference ?: orderId

    fun toState() = SimulatedPaymentState(paymentId, settled ?: (outcome != null), callbacksSent)
}

data class PageInfoResource(
    val number: Int = 0,
    val totalPages: Int = 0,
)

data class SimulatedPaymentPage(
    val data: List<SimulatedPaymentResource> = emptyList(),
    val page: PageInfoResource = PageInfoResource(),
)

class HttpSimulatorControls : SimulatorControls {
    override fun at(
        baseUrl: String,
        timeout: Duration,
    ): SimulatorControl = HttpSimulatorControl(baseUrl, JdkTransport(timeout))
}

class HttpSimulatorControl(
    private val baseUrl: String,
    private val http: Transport,
) : SimulatorControl {
    override fun declareScenario(
        provider: String,
        reference: String,
        scenario: String,
    ) = put("/control/operators/${segment(provider)}/references/${segment(reference)}/scenario", mapOf("scenario" to scenario))

    override fun defaultScenario(
        provider: String,
        scenario: String,
    ) = put("/control/operators/${segment(provider)}/default-scenario", mapOf("scenario" to scenario))

    override fun payment(
        provider: String,
        reference: String,
    ): SimulatedPaymentState? {
        var number = 0
        while (true) {
            val page = read<SimulatedPaymentPage>("/control/payments?operator=${encode(provider)}&q=${encode(reference)}&page=$number&size=$PAGE_SIZE")
            page.data.firstOrNull { it.merchantReference == reference }?.let { return it.toState() }
            if (page.data.isEmpty() || ++number >= page.page.totalPages || number >= MAX_PAGES) return null
        }
    }

    override fun resolve(
        paymentId: String,
        outcome: String,
        reason: String?,
    ) = post("/control/payments/${segment(paymentId)}/resolve", mapOf("outcome" to outcome, "reason" to reason))

    override fun callback(paymentId: String) = post("/control/payments/${segment(paymentId)}/callback", null)

    override fun reset() = post("/control/reset", null)

    private inline fun <reified T> read(path: String): T {
        val exchange = send("GET", path, null)
        return try {
            json.readValue<T>(exchange.body)
        } catch (exception: JacksonException) {
            throw PreparationException("the mock server answered GET $path with an unreadable body: ${exception.originalMessage}")
        }
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
        val headers = if (payload != null) mapOf(Expectations.CONTENT_TYPE to Expectations.JSON) else emptyMap()
        val exchange =
            try {
                http.send(OutgoingRequest(method, baseUrl.trimEnd('/') + path, headers, payload))
            } catch (exception: TransportException) {
                throw PreparationException("the mock server is unreachable: ${exception.message}")
            }
        if (exchange.status !in SUCCESS) throw PreparationException("the mock server refused $method $path with ${exchange.status} ${exchange.text}".trim())
        return exchange
    }

    private fun segment(value: String): String = encode(value).replace("+", "%20")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        val SUCCESS = 200..299
        val json: JsonMapper =
            JsonMapper
                .builder()
                .addModule(KotlinModule.Builder().build())
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
    }
}

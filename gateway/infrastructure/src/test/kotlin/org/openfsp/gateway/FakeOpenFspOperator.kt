package org.openfsp.gateway

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.WebhookSigner
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

data class FakeProfile(
    val capabilities: List<String>,
    val nextAction: String,
    val payerRequired: Boolean = false,
    val returnUrlRequired: Boolean = false,
    val locales: List<String> = emptyList(),
)

data class SignedCallback(
    val body: String,
    val headers: Map<String, String>,
)

class FakeOpenFspOperator(
    private val profiles: Map<String, FakeProfile>,
    private val publicBaseUrl: String = "http://127.0.0.1:8080",
) : AutoCloseable {
    private class SigningKey(
        val kid: String,
        val pair: KeyPair,
        @Volatile var status: String,
    )

    val calls = AtomicInteger()
    val capabilityReads = AtomicInteger()
    val statuses = ConcurrentHashMap<String, String>()
    val reasons = ConcurrentHashMap<String, String>()
    val details = ConcurrentHashMap<String, String>()
    private val amounts = ConcurrentHashMap<String, Long>()
    val callbackUrls = ConcurrentHashMap<String, String>()
    val requests = CopyOnWriteArrayList<Pair<Map<String, String>, String>>()
    val statementLines = CopyOnWriteArrayList<String>()
    private val keys = CopyOnWriteArrayList<SigningKey>()
    private val generated = AtomicInteger()

    @Volatile
    var statementClosed: Boolean = false

    @Volatile
    var failWith: Int? = null

    @Volatile
    var expiresIn: Long = 900

    @Volatile
    var errorMessage: String = "down"

    @Volatile
    var loseCreationAnswers: Boolean = false

    @Volatile
    var nextActionOverride: String? = null

    init {
        rotateKey()
    }

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> exchange.use { handle(it) } }
            start()
        }

    fun url(operator: String): String = "http://127.0.0.1:${server.address.port}/$operator"

    fun reset() {
        failWith = null
        loseCreationAnswers = false
        expiresIn = 900
        errorMessage = "down"
        nextActionOverride = null
        statementLines.clear()
        statementClosed = false
    }

    fun rotateKey(retirePrevious: Boolean = true) {
        keys.forEach { it.status = if (retirePrevious) "retired" else "removed" }
        keys.removeIf { it.status == "removed" }
        keys += SigningKey("whk_fake_${generated.incrementAndGet()}", KeyPairGenerator.getInstance("Ed25519").generateKeyPair(), "active")
    }

    fun dropRetiredKeys() {
        keys.removeIf { it.status == "retired" }
    }

    fun statementLine(
        reference: String,
        orderId: String?,
        amount: Long,
        bookedAtLocal: String,
    ) {
        val bookedAt = LocalDateTime.parse(bookedAtLocal).atZone(HAITI).toInstant()
        statementLines += """{"provider_reference":"$reference",${orderId?.let { "\"provider_order_id\":\"$it\"," } ?: ""}"type":"payment","amount":{"amount":$amount,"currency":"HTG"},"booked_at":"$bookedAt","match":"${if (orderId == null) "unmatched" else "matched"}"}"""
    }

    fun event(
        orderId: String,
        status: String,
        amount: Long = 125000,
        reason: String? = null,
        operator: String = "mock_alpha",
    ): String {
        val data = payment(operator, orderId, status, amount, reason)
        return """{"id":"evt_$orderId","type":"payment.$status","created_at":"${Instant.now()}","sequence":2,"resource_type":"payment","resource_id":"MA-1","reference":"$orderId","previous_status":"pending","data":$data}"""
    }

    fun callback(
        orderId: String,
        status: String,
        amount: Long = 125000,
        reason: String? = null,
        operator: String = "mock_alpha",
        signingKey: Int = keys.lastIndex,
    ): SignedCallback {
        val body = event(orderId, status, amount, reason, operator)
        val target = callbackUrls[orderId] ?: "$publicBaseUrl/callbacks/$operator"
        val key = keys[signingKey]
        val headers = WebhookSigner(key.pair.private, key.kid).sign(target, body.toByteArray(), "evt_$orderId", "payment.$status")
        return SignedCallback(body, headers)
    }

    private fun handle(exchange: HttpExchange) {
        val segments = exchange.requestURI.path.removePrefix("/").split('/')
        val operator = segments.first()
        val profile = profiles[operator] ?: return exchange.send(404, "{}")
        when (segments.drop(1).joinToString("/")) {
            ".well-known/openfsp-configuration" -> exchange.send(200, configuration(operator))
            "v1/capabilities" -> {
                capabilityReads.incrementAndGet()
                exchange.send(200, capabilities(operator, profile))
            }
            "v1/statements" -> exchange.send(200, statement(operator, query(exchange).getValue("business_date")))
            "v1/payments" -> payments(exchange, operator)
            else -> exchange.send(404, "{}")
        }
    }

    private fun payments(
        exchange: HttpExchange,
        operator: String,
    ) {
        calls.incrementAndGet()
        failWith?.let { return exchange.send(it, problem(it)) }
        if (exchange.requestMethod != "POST") {
            val orderId = query(exchange).getValue("reference")
            val found = statuses[orderId]?.let { payment(operator, orderId, it, amounts[orderId] ?: 125000, reasons[orderId]) }
            return exchange.send(200, """{"data":[${found ?: ""}]}""")
        }
        val body = exchange.requestBody.readAllBytes().decodeToString()
        requests += exchange.requestHeaders.entries.associate { (name, values) -> name.lowercase() to values.first() } to body
        val orderId = Regex("\"reference\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        statuses.putIfAbsent(orderId, "pending")
        amounts.putIfAbsent(orderId, Regex("\"amount\":\\{\"amount\":(\\d+)").find(body)!!.groupValues[1].toLong())
        Regex("\"callback_url\":\"([^\"]+)\"").find(body)?.let { callbackUrls[orderId] = it.groupValues[1] }
        if (loseCreationAnswers) return exchange.send(500, problem(500))
        exchange.send(201, payment(operator, orderId, statuses.getValue(orderId), amounts.getValue(orderId), reasons[orderId]))
    }

    private fun payment(
        operator: String,
        orderId: String,
        status: String,
        amount: Long,
        reason: String?,
    ): String {
        val now = Instant.now()
        val pending = status == "pending"
        val action = nextActionOverride ?: profiles.getValue(operator).nextAction
        val nextAction =
            if (action == "redirect") """{"type":"redirect","redirect_url":"https://fake.example/checkout/$orderId","expires_at":"${now.plusSeconds(expiresIn)}"}""" else """{"type":"$action"}"""
        val failure =
            if (status == "failed") {
                (reason?.let { ",\"failure_reason\":\"$it\"" } ?: "") + (details[orderId]?.let { ",\"failure_detail\":{\"code\":\"E42\",\"message\":\"$it\"}" } ?: "")
            } else {
                ""
            }
        return """{"id":"MA-1","reference":"$orderId","status":"$status","amount":{"amount":$amount,"currency":"HTG"},"provider":"$operator","provider_reference":"MA-1","provider_order_id":"$orderId",""" +
            (if (pending) """"next_action":$nextAction,""" else """"completed_at":"$now",""") +
            """"created_at":"$now","updated_at":"$now","expires_at":"${now.plusSeconds(expiresIn)}"$failure}"""
    }

    private fun problem(status: Int) =
        """{"type":"https://openfsp.org/problems/internal-error","title":"Unexpected failure","status":$status,"detail":"$errorMessage","retryable":true,"effect":"unknown","request_id":"req_1"}"""

    private fun configuration(operator: String): String {
        val jwks =
            keys.joinToString(",") {
                """{"kid":"${it.kid}","kty":"OKP","crv":"Ed25519","alg":"EdDSA","use":"sig","x":"${Ed25519Keys.toRaw(it.pair.public)}","openfsp_status":"${it.status}"}"""
            }
        val base = url(operator)
        return """{"issuer":"$base","protocol_version":"0.1.0","api_base":"$base/v1","payments_endpoint":"$base/v1/payments","capabilities_endpoint":"$base/v1/capabilities","webhook_keys":[$jwks]}"""
    }

    private fun capabilities(
        operator: String,
        profile: FakeProfile,
    ): String {
        val list = { values: List<String> -> values.joinToString(",") { "\"$it\"" } }
        return """{"protocol_version":"0.1.0","providers":[{"id":"$operator","display_name":"Fake $operator","capabilities":[${list(profile.capabilities)}],""" +
            """"payments":{"next_actions":["${profile.nextAction}"],"payer_required":${profile.payerRequired},"return_url_required":${profile.returnUrlRequired},"expiry_guaranteed":false,"locales":[${list(profile.locales)}]}}]}"""
    }

    private fun statement(
        operator: String,
        day: String,
    ) = """{"data":[{"id":"stm_1","provider":"$operator","business_date":"$day","complete":$statementClosed,"entries":[${statementLines.joinToString(",")}],"missing_payments":[]}]}"""

    private fun query(exchange: HttpExchange): Map<String, String> =
        exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun HttpExchange.send(
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", if (status >= 400) "application/problem+json" else "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)

    private companion object {
        val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")
    }
}

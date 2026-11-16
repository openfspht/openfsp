package org.openfsp.validator.engine

import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.PublicKeys
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.validator.suite.CheckSpec
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

fun interface Check {
    fun run(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonMapper,
        earlier: (String) -> Exchange?,
    ): List<Mismatch>
}

object Checks {
    const val POINTERS_RESOLVE_IN_REQUEST = "pointers-resolve-in-request"
    const val HTTP_MESSAGE_SIGNATURE = "http-message-signature"
    val CLOCK_TOLERANCE: Duration = Duration.ofSeconds(60)

    private val registry: Map<String, Check> =
        mapOf(
            POINTERS_RESOLVE_IN_REQUEST to Check { spec, exchange, json, _ -> pointersResolveInRequest(spec, exchange, json) },
            HTTP_MESSAGE_SIGNATURE to Check(::httpMessageSignature),
        )

    val names: Set<String> get() = registry.keys

    fun find(name: String): Check? = registry[name]

    private fun pointersResolveInRequest(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonMapper,
    ): List<Mismatch> {
        val selector = spec.pointer ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a pointer argument", "none"))
        val response = exchange.json(json) ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON response", exchange.text))
        val sent = exchange.request.body?.let { runCatching { json.readTree(it) }.getOrNull() } ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON request body", "none"))
        val pointers = Pointers.resolve(response, selector)
        return pointers.mapNotNull { (location, node) ->
            val label = "$POINTERS_RESOLVE_IN_REQUEST $location"
            when {
                !node.isString -> Mismatch(label, "a JSON Pointer string", node.toString())
                !Pointers.isValidPointer(node.asString()) -> Mismatch(label, "a JSON Pointer (RFC 6901)", node.asString())
                !designates(sent, node.asString()) -> Mismatch(label, "a location in the request body as sent", node.asString())
                else -> null
            }
        }
    }

    private fun designates(
        sent: JsonNode,
        pointer: String,
    ): Boolean {
        if (pointer.isEmpty() || Pointers.resolve(sent, pointer).isNotEmpty()) return true
        val parent = pointer.substringBeforeLast('/')
        return Pointers.resolve(sent, parent).singleOrNull()?.node?.isObject == true
    }

    private fun httpMessageSignature(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonMapper,
        earlier: (String) -> Exchange?,
    ): List<Mismatch> {
        fun mismatch(
            expected: String,
            observed: String,
        ) = listOf(Mismatch(HTTP_MESSAGE_SIGNATURE, expected, observed))
        val source = spec.from ?: return mismatch("a from argument naming the step that read the key set", "none")
        val document = earlier(source)?.json(json) ?: return mismatch("a JSON key set read by step $source", "no such JSON document")
        val keySet = spec.pointer?.let { Pointers.resolve(document, it).singleOrNull()?.node } ?: document
        if (!keySet.isArray) return mismatch("an array of JWK at ${spec.pointer ?: "the document root"}", keySet.toString())
        val keys = keySet.mapNotNull(::publicKey).toMap()
        val receivedAt = exchange.receivedAt ?: Instant.now()
        val verifier = WebhookVerifier(PublicKeys { keys[it] }, InMemoryNonceStore(), Clock.fixed(receivedAt, ZoneOffset.UTC))
        val verification = verifier.verify(exchange.request.method, exchange.request.url, exchange::header, exchange.body)
        if (verification is Verification.Rejected) return mismatch("a signature verified with a published key", verification.reason)
        val created = exchange.header(WebhookHeaders.SIGNATURE_INPUT)?.let(SignatureParams::parse)?.created ?: return mismatch("a created parameter", "none")
        val skew = abs(created - receivedAt.epochSecond)
        if (skew > CLOCK_TOLERANCE.seconds) return mismatch("created within ${CLOCK_TOLERANCE.seconds}s of the delivery received at $receivedAt", "created=$created, ${skew}s away")
        return emptyList()
    }

    private fun publicKey(jwk: JsonNode): Pair<String, PublicKey>? {
        fun member(name: String) = jwk.path(name).takeIf { it.isString }?.asString()
        val status = member(STATUS)?.takeIf { KeyStatus.fromWire(it) != null } ?: return null
        val kid = member("kid") ?: return null
        val key =
            WebhookJwk(
                kid = kid,
                kty = member("kty") ?: return null,
                crv = member("crv") ?: return null,
                alg = member("alg") ?: return null,
                use = member("use") ?: return null,
                x = member("x") ?: return null,
                openfspStatus = status,
            ).publicKeyOrNull() ?: return null
        return kid to key
    }

    private const val STATUS = "openfsp_status"
}

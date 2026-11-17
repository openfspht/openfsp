package org.openfsp.validator.engine

import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.PublicKeys
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.Verification
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.protocol.webhook.WebhookVerifier
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.suite.CheckSpec
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
        json: JsonCodec,
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

    fun find(name: String): Check? = registry[name]

    private fun pointersResolveInRequest(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonCodec,
    ): List<Mismatch> {
        val selector = spec.pointer ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a pointer argument", "none"))
        val response = exchange.json(json) ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON response", exchange.text))
        val sent = exchange.request.body?.let(json::parse) ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON request body", "none"))
        val pointers = Pointers.resolve(response, selector)
        return pointers.mapNotNull { (location, node) ->
            val label = "$POINTERS_RESOLVE_IN_REQUEST $location"
            when {
                node !is JsonString -> Mismatch(label, "a JSON Pointer string", json.text(node))
                !Pointers.isValidPointer(node.value) -> Mismatch(label, "a JSON Pointer (RFC 6901)", node.value)
                !designates(sent, node.value) -> Mismatch(label, "a location in the request body as sent", node.value)
                else -> null
            }
        }
    }

    private fun designates(
        sent: Json,
        pointer: String,
    ): Boolean {
        if (pointer.isEmpty() || Pointers.resolve(sent, pointer).isNotEmpty()) return true
        val parent = pointer.substringBeforeLast('/')
        return Pointers.resolve(sent, parent).singleOrNull()?.node is JsonObject
    }

    private fun httpMessageSignature(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonCodec,
        earlier: (String) -> Exchange?,
    ): List<Mismatch> {
        fun mismatch(
            expected: String,
            observed: String,
        ) = listOf(Mismatch(HTTP_MESSAGE_SIGNATURE, expected, observed))
        val source = spec.from ?: return mismatch("a from argument naming the step that read the key set", "none")
        val document = earlier(source)?.json(json) ?: return mismatch("a JSON key set read by step $source", "no such JSON document")
        val keySet = spec.pointer?.let { Pointers.resolve(document, it).singleOrNull()?.node } ?: document
        if (keySet !is JsonArray) return mismatch("an array of JWK at ${spec.pointer ?: "the document root"}", json.text(keySet))
        val keys = keySet.items.mapNotNull(::publicKey).toMap()
        val receivedAt = exchange.receivedAt ?: Instant.now()
        val verifier = WebhookVerifier(PublicKeys { keys[it] }, InMemoryNonceStore(), Clock.fixed(receivedAt, ZoneOffset.UTC))
        val verification = verifier.verify(exchange.request.method, exchange.request.url, exchange::header, exchange.body)
        if (verification is Verification.Rejected) return mismatch("a signature verified with a published key", verification.reason)
        val created = exchange.header(WebhookHeaders.SIGNATURE_INPUT)?.let(SignatureParams::parse)?.created ?: return mismatch("a created parameter", "none")
        val skew = abs(created - receivedAt.epochSecond)
        if (skew > CLOCK_TOLERANCE.seconds) return mismatch("created within ${CLOCK_TOLERANCE.seconds}s of the delivery received at $receivedAt", "created=$created, ${skew}s away")
        return emptyList()
    }

    private fun publicKey(jwk: Json): Pair<String, PublicKey>? {
        fun member(name: String) = (jwk as? JsonObject)?.get(name)?.string
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

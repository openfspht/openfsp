package org.openfsp.validator.engine

import org.openfsp.ddd.DomainService
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
import java.time.ZoneOffset
import kotlin.math.abs

fun interface Check {
    fun run(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonCodec,
        earlier: (String) -> Exchange?,
        nonces: DeliveryNonces,
        clock: Clock,
    ): List<Mismatch>
}

@DomainService
class Checks(
    private val pointers: Pointers,
    private val keys: JwkKeys,
) {
    private val registry: Map<String, Check> =
        mapOf(
            POINTERS_RESOLVE_IN_REQUEST to Check { spec, exchange, json, _, _, _ -> pointersResolveInRequest(spec, exchange, json) },
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
        val located = pointers.resolve(response, selector)
        return located.mapNotNull { (location, node) ->
            val label = "$POINTERS_RESOLVE_IN_REQUEST $location"
            when {
                node !is JsonString -> Mismatch(label, "a JSON Pointer string", json.text(node))
                !pointers.isValidPointer(node.value) -> Mismatch(label, "a JSON Pointer (RFC 6901)", node.value)
                !designates(sent, node.value) -> Mismatch(label, "a location in the request body as sent", node.value)
                else -> null
            }
        }
    }

    private fun designates(
        sent: Json,
        pointer: String,
    ): Boolean {
        if (pointer.isEmpty() || pointers.resolve(sent, pointer).isNotEmpty()) return true
        val parent = pointer.substringBeforeLast('/')
        return pointers.resolve(sent, parent).singleOrNull()?.node is JsonObject
    }

    private fun httpMessageSignature(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonCodec,
        earlier: (String) -> Exchange?,
        nonces: DeliveryNonces,
        clock: Clock,
    ): List<Mismatch> {
        fun mismatch(
            expected: String,
            observed: String,
        ) = listOf(Mismatch(HTTP_MESSAGE_SIGNATURE, expected, observed))
        val source = spec.from ?: return mismatch("a from argument naming the step that read the key set", "none")
        val document = earlier(source)?.json(json) ?: return mismatch("a JSON key set read by step $source", "no such JSON document")
        val keySet = spec.pointer?.let { pointers.resolve(document, it).singleOrNull()?.node } ?: document
        if (keySet !is JsonArray) return mismatch("an array of JWK at ${spec.pointer ?: "the document root"}", json.text(keySet))
        val keys = keySet.items.mapNotNull(::publicKey).toMap()
        val receivedAt = exchange.receivedAt ?: clock.instant()
        val delivery = "$receivedAt ${exchange.header(WebhookHeaders.SIGNATURE)}"
        val verifier = WebhookVerifier(PublicKeys { keys[it] }, nonces.forDelivery(delivery), Clock.fixed(receivedAt, ZoneOffset.UTC))
        val verification = verifier.verify(exchange.request.method, exchange.request.url, exchange::header, exchange.body)
        if (verification is Verification.Rejected) return mismatch("a signature verified with a published key and a nonce unseen in this run", verification.reason)
        val params = exchange.header(WebhookHeaders.SIGNATURE_INPUT)?.let(SignatureParams::parse) ?: return mismatch("a created parameter", "none")
        val skew = abs(params.created - receivedAt.epochSecond)
        if (skew > CLOCK_TOLERANCE.seconds) return mismatch("created within ${CLOCK_TOLERANCE.seconds}s of the delivery received at $receivedAt", "created=${params.created}, ${skew}s away")
        spec.renewedFrom?.let { name ->
            val before = earlier(name)?.header(WebhookHeaders.SIGNATURE_INPUT)?.let(SignatureParams::parse) ?: return mismatch("a signed delivery selected by step $name", "none")
            if (before.nonce == params.nonce) return mismatch("a nonce other than the one of $name", params.nonce)
            if (before.created == params.created) return mismatch("a created other than the one of $name", params.created.toString())
        }
        return emptyList()
    }

    private fun publicKey(jwk: Json): Pair<String, PublicKey>? {
        fun member(name: String) = (jwk as? JsonObject)?.get(name)?.string
        member(STATUS)?.takeIf { KeyStatus.fromWire(it) != null } ?: return null
        val kid = member("kid") ?: return null
        if (member("use") != WebhookJwk.SIG) return null
        val key =
            when (member("kty")) {
                WebhookJwk.OKP -> keys.ed25519(member("crv"), member("alg"), member("x"))
                JwkKeys.EC -> keys.p256(member("crv"), member("alg"), member("x"), member("y"))
                else -> null
            } ?: return null
        return kid to key
    }

    companion object {
        const val POINTERS_RESOLVE_IN_REQUEST = "pointers-resolve-in-request"
        const val HTTP_MESSAGE_SIGNATURE = "http-message-signature"
        val CLOCK_TOLERANCE: Duration = Duration.ofSeconds(60)
        private const val STATUS = "openfsp_status"
    }
}

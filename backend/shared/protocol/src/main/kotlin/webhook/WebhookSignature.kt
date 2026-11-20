package org.openfsp.protocol.webhook

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object WebhookHeaders {
    const val CONTENT_TYPE = "Content-Type"
    const val CONTENT_DIGEST = "Content-Digest"
    const val EVENT_ID = "OpenFSP-Event-Id"
    const val EVENT_TYPE = "OpenFSP-Event-Type"
    const val DELIVERY_ATTEMPT = "OpenFSP-Delivery-Attempt"
    const val SIGNATURE_INPUT = "Signature-Input"
    const val SIGNATURE = "Signature"
}

object ContentDigest {
    fun sha256(body: ByteArray): String = "sha-256=:${Base64.getEncoder().encodeToString(digest(body))}:"

    internal fun digest(body: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(body)
}

data class SignedMessage(
    val method: String,
    val targetUri: String,
    val contentType: String,
    val contentDigest: String,
    val eventId: String,
    val eventType: String,
)

data class SignatureParams(
    val created: Long,
    val expires: Long,
    val keyId: String,
    val nonce: String,
    val alg: String,
    val tag: String,
) {
    fun serialize(): String =
        "$COMPONENT_LIST;created=$created;expires=$expires;keyid=\"$keyId\";nonce=\"$nonce\";alg=\"$alg\";tag=\"$tag\""

    companion object {
        const val COMPONENT_LIST =
            "(\"@method\" \"@target-uri\" \"content-type\" \"content-digest\" \"openfsp-event-id\" \"openfsp-event-type\")"
        const val TAG = "openfsp-webhook"
        const val ED25519 = "ed25519"
        const val ECDSA_P256_SHA256 = "ecdsa-p256-sha256"
        const val LABEL = "sig1"
        val COMPONENTS: List<String> = listOf("@method", "@target-uri", "content-type", "content-digest", "openfsp-event-id", "openfsp-event-type")

        fun parse(signatureInput: String): SignatureParams? = (ReceivedSignature.select(signatureInput) as? ReceivedSignature.Selected)?.signature?.params
    }
}

internal class ReceivedSignature(
    val label: String,
    val params: SignatureParams,
    val serializedParams: String,
) {
    sealed interface Selection

    data class Selected(
        val signature: ReceivedSignature,
    ) : Selection

    data class Refused(
        val reason: String,
    ) : Selection

    companion object {
        fun select(signatureInput: String): Selection {
            val members = StructuredFields.parseDictionary(signatureInput) ?: return Refused("malformed Signature-Input")
            val tagged = members.filter { (_, member) -> member is InnerList && member.parameters["tag"] == BareItem.StringItem(SignatureParams.TAG) }
            val (label, member) = tagged.entries.singleOrNull() ?: return Refused(if (tagged.isEmpty()) "no ${SignatureParams.TAG} signature" else "several ${SignatureParams.TAG} signatures")
            val list = member as InnerList
            val components = list.items.map { (it.value as? BareItem.StringItem)?.value.takeIf { _ -> it.parameters.isEmpty() } }
            if (components != SignatureParams.COMPONENTS) return Refused("covered components differ from the specification")
            val params =
                SignatureParams(
                    created = list.integer("created") ?: return Refused("missing or malformed created"),
                    expires = list.integer("expires") ?: return Refused("missing or malformed expires"),
                    keyId = list.string("keyid") ?: return Refused("missing or malformed keyid"),
                    nonce = list.string("nonce") ?: return Refused("missing or malformed nonce"),
                    alg = list.string("alg") ?: return Refused("missing or malformed alg"),
                    tag = SignatureParams.TAG,
                )
            return Selected(ReceivedSignature(label, params, StructuredFields.serialize(list)))
        }

        private fun InnerList.integer(name: String): Long? = (parameters[name] as? BareItem.IntegerItem)?.value

        private fun InnerList.string(name: String): String? = (parameters[name] as? BareItem.StringItem)?.value
    }
}

object SignatureBase {
    fun of(
        message: SignedMessage,
        params: SignatureParams,
    ): ByteArray = of(message, params.serialize())

    internal fun of(
        message: SignedMessage,
        serializedParams: String,
    ): ByteArray =
        listOf(
            "\"@method\": ${message.method.uppercase()}",
            "\"@target-uri\": ${message.targetUri}",
            "\"content-type\": ${message.contentType}",
            "\"content-digest\": ${message.contentDigest}",
            "\"openfsp-event-id\": ${message.eventId}",
            "\"openfsp-event-type\": ${message.eventType}",
            "\"@signature-params\": $serializedParams",
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
}

class WebhookSigner(
    private val privateKey: PrivateKey,
    private val keyId: String,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        require(WebhookAlgorithm.of(privateKey) == WebhookAlgorithm.ED25519) { "webhooks are signed with Ed25519" }
    }

    fun sign(
        targetUri: String,
        body: ByteArray,
        eventId: String,
        eventType: String,
    ): Map<String, String> {
        val message =
            SignedMessage(
                method = "POST",
                targetUri = targetUri,
                contentType = JSON,
                contentDigest = ContentDigest.sha256(body),
                eventId = eventId,
                eventType = eventType,
            )
        val created = clock.instant().epochSecond
        val params =
            SignatureParams(
                created = created,
                expires = created + MAX_VALIDITY.seconds,
                keyId = keyId,
                nonce = nonce(),
                alg = SignatureParams.ED25519,
                tag = SignatureParams.TAG,
            )
        val signature =
            Signature.getInstance(WebhookAlgorithm.ED25519.jca).run {
                initSign(privateKey)
                update(SignatureBase.of(message, params))
                sign()
            }
        return mapOf(
            WebhookHeaders.CONTENT_TYPE to message.contentType,
            WebhookHeaders.CONTENT_DIGEST to message.contentDigest,
            WebhookHeaders.EVENT_ID to eventId,
            WebhookHeaders.EVENT_TYPE to eventType,
            WebhookHeaders.SIGNATURE_INPUT to "${SignatureParams.LABEL}=${params.serialize()}",
            WebhookHeaders.SIGNATURE to "${SignatureParams.LABEL}=:${Base64.getEncoder().encodeToString(signature)}:",
        )
    }

    private fun nonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

fun interface PublicKeys {
    fun resolve(keyId: String): PublicKey?
}

interface NonceStore {
    fun reserve(
        nonce: String,
        until: Instant,
    ): Boolean

    fun release(nonce: String)
}

class InMemoryNonceStore(
    private val clock: Clock = Clock.systemUTC(),
) : NonceStore {
    private val nonces = ConcurrentHashMap<String, Instant>()

    override fun reserve(
        nonce: String,
        until: Instant,
    ): Boolean {
        val now = clock.instant()
        nonces.entries.removeIf { it.value.isBefore(now) }
        return nonces.putIfAbsent(nonce, until) == null
    }

    override fun release(nonce: String) {
        nonces.remove(nonce)
    }
}

sealed interface Verification {
    data object Verified : Verification

    data class Rejected(
        val reason: String,
    ) : Verification
}

class WebhookVerifier(
    private val keys: PublicKeys,
    private val nonces: NonceStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun verify(
        method: String,
        targetUri: String,
        headers: (String) -> String?,
        body: ByteArray,
    ): Verification {
        val input = headers(WebhookHeaders.SIGNATURE_INPUT) ?: return Verification.Rejected("missing Signature-Input")
        val received =
            when (val selection = ReceivedSignature.select(input)) {
                is ReceivedSignature.Refused -> return Verification.Rejected(selection.reason)
                is ReceivedSignature.Selected -> selection.signature
            }
        val params = received.params
        val key = keys.resolve(params.keyId) ?: return Verification.Rejected("unknown keyid")
        val algorithm = WebhookAlgorithm.of(key) ?: return Verification.Rejected("unsupported key type")
        if (params.alg != algorithm.wire) return Verification.Rejected("alg does not match the key")

        val now = clock.instant().epochSecond
        if (params.created < 0 || params.expires < params.created) return Verification.Rejected("invalid validity window")
        if (params.expires - params.created > MAX_VALIDITY.seconds) return Verification.Rejected("validity window too long")
        if (now > params.expires) return Verification.Rejected("signature expired")
        if (params.created > now + MAX_VALIDITY.seconds) return Verification.Rejected("signature created in the future")

        val digestHeader = headers(WebhookHeaders.CONTENT_DIGEST)?.trim() ?: return Verification.Rejected("missing Content-Digest")
        val digest =
            StructuredFields.parseDictionary(digestHeader)?.get(SHA_256)?.let { it as? Item }?.value as? BareItem.ByteSequence
                ?: return Verification.Rejected("Content-Digest has no sha-256 member")
        if (!MessageDigest.isEqual(digest.value, ContentDigest.digest(body))) return Verification.Rejected("Content-Digest does not match the body")

        val message =
            SignedMessage(
                method = method,
                targetUri = targetUri,
                contentType = headers(WebhookHeaders.CONTENT_TYPE)?.trim() ?: return Verification.Rejected("missing Content-Type"),
                contentDigest = digestHeader,
                eventId = headers(WebhookHeaders.EVENT_ID)?.trim() ?: return Verification.Rejected("missing OpenFSP-Event-Id"),
                eventType = headers(WebhookHeaders.EVENT_TYPE)?.trim() ?: return Verification.Rejected("missing OpenFSP-Event-Type"),
            )
        val signature =
            headers(WebhookHeaders.SIGNATURE)
                ?.let(StructuredFields::parseDictionary)
                ?.get(received.label)
                ?.let { (it as? Item)?.value as? BareItem.ByteSequence }
                ?: return Verification.Rejected("missing or malformed Signature")
        val valid =
            Signature.getInstance(algorithm.jca).run {
                initVerify(key)
                update(SignatureBase.of(message, received.serializedParams))
                runCatching { verify(signature.value) }.getOrDefault(false)
            }
        if (!valid) return Verification.Rejected("invalid signature")
        if (!nonces.reserve(params.nonce, Instant.ofEpochSecond(params.expires))) return Verification.Rejected("nonce already used")
        return Verification.Verified
    }
}

private const val JSON = "application/json"
private const val NONCE_BYTES = 16
private const val SHA_256 = "sha-256"
private val MAX_VALIDITY: Duration = Duration.ofSeconds(300)

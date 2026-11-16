package org.openfsp.protocol.webhook

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.EdECKey
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
        const val LABEL = "sig1"

        fun parse(signatureInput: String): SignatureParams? {
            val value = signatureInput.trim().removePrefix("$LABEL=").takeIf { it.startsWith(COMPONENT_LIST) } ?: return null
            val params =
                value
                    .removePrefix(COMPONENT_LIST)
                    .split(';')
                    .filter { it.isNotBlank() }
                    .associate { part ->
                        val (key, raw) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return null
                        key to raw.removeSurrounding("\"")
                    }
            return SignatureParams(
                created = params["created"]?.toLongOrNull() ?: return null,
                expires = params["expires"]?.toLongOrNull() ?: return null,
                keyId = params["keyid"] ?: return null,
                nonce = params["nonce"] ?: return null,
                alg = params["alg"] ?: return null,
                tag = params["tag"] ?: return null,
            )
        }
    }
}

object SignatureBase {
    fun of(
        message: SignedMessage,
        params: SignatureParams,
    ): ByteArray =
        listOf(
            "\"@method\": ${message.method.uppercase()}",
            "\"@target-uri\": ${message.targetUri}",
            "\"content-type\": ${message.contentType}",
            "\"content-digest\": ${message.contentDigest}",
            "\"openfsp-event-id\": ${message.eventId}",
            "\"openfsp-event-type\": ${message.eventType}",
            "\"@signature-params\": ${params.serialize()}",
        ).joinToString("\n").toByteArray(Charsets.UTF_8)
}

class WebhookSigner(
    private val privateKey: PrivateKey,
    private val keyId: String,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        require(privateKey.isEd25519()) { "webhooks are signed with Ed25519" }
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
            Signature.getInstance(ED25519_JCA).run {
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
        val params =
            headers(WebhookHeaders.SIGNATURE_INPUT)?.let(SignatureParams::parse)
                ?: return Verification.Rejected("missing or malformed Signature-Input")
        if (params.tag != SignatureParams.TAG) return Verification.Rejected("unexpected tag")
        val key = keys.resolve(params.keyId) ?: return Verification.Rejected("unknown keyid")
        if (!key.isEd25519() || params.alg != SignatureParams.ED25519) return Verification.Rejected("alg does not match the key")

        val now = clock.instant().epochSecond
        if (params.created < 0 || params.expires < params.created) return Verification.Rejected("invalid validity window")
        if (params.expires - params.created > MAX_VALIDITY.seconds) return Verification.Rejected("validity window too long")
        if (now > params.expires) return Verification.Rejected("signature expired")
        if (params.created > now + MAX_VALIDITY.seconds) return Verification.Rejected("signature created in the future")
        if (!nonces.reserve(params.nonce, Instant.ofEpochSecond(params.expires))) return Verification.Rejected("nonce already used")

        val verification = runCatching { verifyContent(method, targetUri, headers, body, params, key) }
        if (verification.getOrNull() !is Verification.Verified) nonces.release(params.nonce)
        return verification.getOrThrow()
    }

    private fun verifyContent(
        method: String,
        targetUri: String,
        headers: (String) -> String?,
        body: ByteArray,
        params: SignatureParams,
        key: PublicKey,
    ): Verification {
        val digestHeader = headers(WebhookHeaders.CONTENT_DIGEST) ?: return Verification.Rejected("missing Content-Digest")
        val expectedDigest = ContentDigest.sha256(body)
        if (!MessageDigest.isEqual(digestHeader.toByteArray(), expectedDigest.toByteArray())) {
            return Verification.Rejected("Content-Digest does not match the body")
        }

        val message =
            SignedMessage(
                method = method,
                targetUri = targetUri,
                contentType = headers(WebhookHeaders.CONTENT_TYPE) ?: return Verification.Rejected("missing Content-Type"),
                contentDigest = digestHeader,
                eventId = headers(WebhookHeaders.EVENT_ID) ?: return Verification.Rejected("missing OpenFSP-Event-Id"),
                eventType = headers(WebhookHeaders.EVENT_TYPE) ?: return Verification.Rejected("missing OpenFSP-Event-Type"),
            )
        val signature =
            headers(WebhookHeaders.SIGNATURE)
                ?.trim()
                ?.removePrefix("${SignatureParams.LABEL}=:")
                ?.removeSuffix(":")
                ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
                ?: return Verification.Rejected("missing or malformed Signature")
        val valid =
            Signature.getInstance(ED25519_JCA).run {
                initVerify(key)
                update(SignatureBase.of(message, params))
                runCatching { verify(signature) }.getOrDefault(false)
            }
        if (!valid) return Verification.Rejected("invalid signature")
        return Verification.Verified
    }
}

private const val JSON = "application/json"
private const val ED25519_JCA = "Ed25519"
private const val NONCE_BYTES = 16
private val MAX_VALIDITY: Duration = Duration.ofSeconds(300)

private fun java.security.Key.isEd25519(): Boolean = (this as? EdECKey)?.params?.name.equals("Ed25519", ignoreCase = true)

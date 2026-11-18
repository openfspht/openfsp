package org.openfsp.sdk.testing

import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.webhook.ContentDigest
import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.P256Keys
import org.openfsp.protocol.webhook.SignatureBase
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.SignedMessage
import org.openfsp.protocol.webhook.WebhookAlgorithm
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.sdk.http.OpenFspClock
import org.openfsp.sdk.model.WebhookKey
import org.openfsp.sdk.webhooks.WebhookDelivery
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.ZoneOffset
import java.util.Base64
import org.openfsp.protocol.webhook.WebhookSigner as ProtocolSigner

class WebhookSigner private constructor(
    private val keys: KeyPair,
    val keyId: String,
    val algorithm: WebhookAlgorithm,
) {
    val key: WebhookKey =
        when (algorithm) {
            WebhookAlgorithm.ED25519 ->
                WebhookKey(keyId, WebhookJwk.OKP, WebhookJwk.ED25519_CURVE, WebhookJwk.EDDSA, WebhookJwk.SIG, Ed25519Keys.toRaw(keys.public), KeyStatus.ACTIVE)
            WebhookAlgorithm.ECDSA_P256_SHA256 -> {
                val (x, y) = P256Keys.toCoordinates(keys.public as ECPublicKey)
                WebhookKey(keyId, WebhookJwk.EC, WebhookJwk.P256_CURVE, WebhookJwk.ES256, WebhookJwk.SIG, x, KeyStatus.ACTIVE, y)
            }
        }

    @JvmOverloads
    fun sign(
        url: String,
        event: PaymentEvent = Wire.event(),
        clock: OpenFspClock = OpenFspClock.SYSTEM,
    ): WebhookDelivery {
        val body = Responses.mapper.writeValueAsBytes(event)
        val headers =
            when (algorithm) {
                WebhookAlgorithm.ED25519 -> ProtocolSigner(keys.private, keyId, Clock.fixed(clock.now(), ZoneOffset.UTC)).sign(url, body, event.id, event.type)
                WebhookAlgorithm.ECDSA_P256_SHA256 -> signP256(url, body, event, clock)
            }
        return WebhookDelivery("POST", url, headers, body)
    }

    private fun signP256(
        url: String,
        body: ByteArray,
        event: PaymentEvent,
        clock: OpenFspClock,
    ): Map<String, String> {
        val message = SignedMessage("POST", url, JSON, ContentDigest.sha256(body), event.id, event.type)
        val created = clock.now().epochSecond
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes))
        val params = SignatureParams(created, created + VALIDITY_SECONDS, keyId, nonce, algorithm.wire, SignatureParams.TAG)
        val signature =
            Signature.getInstance(P1363_ECDSA).run {
                initSign(keys.private)
                update(SignatureBase.of(message, params))
                sign()
            }
        return mapOf(
            WebhookHeaders.CONTENT_TYPE to JSON,
            WebhookHeaders.CONTENT_DIGEST to message.contentDigest,
            WebhookHeaders.EVENT_ID to event.id,
            WebhookHeaders.EVENT_TYPE to event.type,
            WebhookHeaders.SIGNATURE_INPUT to "${SignatureParams.LABEL}=${params.serialize()}",
            WebhookHeaders.SIGNATURE to "${SignatureParams.LABEL}=:${Base64.getEncoder().encodeToString(signature)}:",
        )
    }

    companion object {
        private const val JSON = "application/json"
        private const val NONCE_BYTES = 16
        private const val VALIDITY_SECONDS = 300L
        private const val P1363_ECDSA = "SHA256withECDSAinP1363Format"

        @JvmStatic
        @JvmOverloads
        fun generate(
            keyId: String = "key_test",
            algorithm: WebhookAlgorithm = WebhookAlgorithm.ED25519,
        ): WebhookSigner {
            val keys =
                when (algorithm) {
                    WebhookAlgorithm.ED25519 -> KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
                    WebhookAlgorithm.ECDSA_P256_SHA256 -> KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
                }
            return WebhookSigner(keys, keyId, algorithm)
        }
    }
}

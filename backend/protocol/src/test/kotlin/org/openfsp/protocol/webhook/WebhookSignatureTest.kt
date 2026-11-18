package org.openfsp.protocol.webhook

import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebhookSignatureTest {
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val url = "https://merchant.example/webhooks/openfsp"
    private val body = """{"id":"evt_1","type":"payment.succeeded"}""".toByteArray()

    private fun signer(at: Instant = now) = WebhookSigner(keys.private, "gw-2026-11", Clock.fixed(at, ZoneOffset.UTC))

    private fun verifier(
        at: Instant = now,
        nonces: NonceStore = InMemoryNonceStore(Clock.fixed(at, ZoneOffset.UTC)),
    ) = WebhookVerifier(
        keys = { keyId -> keys.public.takeIf { keyId == "gw-2026-11" } },
        nonces = nonces,
        clock = Clock.fixed(at, ZoneOffset.UTC),
    )

    private fun sign() = signer().sign(url, body, "evt_1", "payment.succeeded")

    private fun Verification.reason() = (this as Verification.Rejected).reason

    @Test
    fun `covers exactly the components of the specification`() {
        val input = sign().getValue(WebhookHeaders.SIGNATURE_INPUT)
        assertTrue(input.startsWith("sig1=(\"@method\" \"@target-uri\" \"content-type\" \"content-digest\" \"openfsp-event-id\" \"openfsp-event-type\");"))
        assertTrue(input.endsWith(";alg=\"ed25519\";tag=\"openfsp-webhook\""))
    }

    @Test
    fun `a signed delivery verifies`() {
        val headers = sign()
        assertIs<Verification.Verified>(verifier().verify("POST", url, headers::get, body))
    }

    @Test
    fun `a replayed nonce is rejected`() {
        val headers = sign()
        val verifier = verifier()
        verifier.verify("POST", url, headers::get, body)
        assertEquals("nonce already used", verifier.verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `concurrent replays of a nonce verify only once`() {
        val headers = sign()
        val verifier = verifier()
        val replays = 16
        val start = CyclicBarrier(replays)
        val pool = Executors.newFixedThreadPool(replays)
        try {
            val results =
                List(replays) {
                    pool.submit<Verification> {
                        start.await()
                        verifier.verify("POST", url, headers::get, body)
                    }
                }.map { it.get() }
            assertEquals(1, results.count { it is Verification.Verified })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a rejected delivery does not consume its nonce`() {
        val headers = sign()
        val verifier = verifier()
        verifier.verify("POST", url, headers::get, "{}".toByteArray())
        assertIs<Verification.Verified>(verifier.verify("POST", url, headers::get, body))
    }

    @Test
    fun `a modified body is rejected`() {
        val headers = sign()
        val tampered = """{"id":"evt_1","type":"payment.failed"}""".toByteArray()
        assertEquals("Content-Digest does not match the body", verifier().verify("POST", url, headers::get, tampered).reason())
    }

    @Test
    fun `a delivery to another URL is rejected`() {
        val headers = sign()
        assertEquals("invalid signature", verifier().verify("POST", "https://attacker.example/hook", headers::get, body).reason())
    }

    @Test
    fun `an expired signature is rejected`() {
        val headers = sign()
        val later = now.plus(Duration.ofSeconds(301))
        assertEquals("signature expired", verifier(at = later).verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `an unknown key id is rejected before anything else`() {
        val headers = WebhookSigner(keys.private, "other", Clock.fixed(now, ZoneOffset.UTC)).sign(url, body, "evt_1", "payment.succeeded")
        assertEquals("unknown keyid", verifier().verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `an unrepresentable validity window is rejected without failing`() {
        val headers = sign().toMutableMap()
        headers[WebhookHeaders.SIGNATURE_INPUT] =
            headers.getValue(WebhookHeaders.SIGNATURE_INPUT).replace(Regex("created=\\d+;expires=\\d+"), "created=${Long.MIN_VALUE};expires=${Long.MAX_VALUE}")
        assertEquals("malformed Signature-Input", verifier().verify("POST", url, headers::get, body).reason())
    }

    private fun signed(
        input: String,
        label: String = "sig1",
        signatureInput: (String) -> String = { "$label=$it" },
        contentDigest: String = ContentDigest.sha256(body),
    ): Map<String, String> {
        val message = SignedMessage("POST", url, "application/json", contentDigest, "evt_1", "payment.succeeded")
        val signature =
            java.security.Signature.getInstance("Ed25519").run {
                initSign(keys.private)
                update(SignatureBase.of(message, input))
                sign()
            }
        return mapOf(
            WebhookHeaders.CONTENT_TYPE to message.contentType,
            WebhookHeaders.CONTENT_DIGEST to contentDigest,
            WebhookHeaders.EVENT_ID to message.eventId,
            WebhookHeaders.EVENT_TYPE to message.eventType,
            WebhookHeaders.SIGNATURE_INPUT to signatureInput(input),
            WebhookHeaders.SIGNATURE to "$label=:${java.util.Base64.getEncoder().encodeToString(signature)}:",
        )
    }

    private val created = now.epochSecond

    private fun params(
        components: String = SignatureParams.COMPONENT_LIST,
        tag: String = SignatureParams.TAG,
        nonce: String = "u3Lq0X4pS2m8Vb1cR7tYzA",
    ) = "$components;created=$created;expires=${created + 300};keyid=\"gw-2026-11\";nonce=\"$nonce\";alg=\"ed25519\";tag=\"$tag\""

    @Test
    fun `parameters in another order verify against the received Signature-Input`() {
        val reordered = "${SignatureParams.COMPONENT_LIST};tag=\"openfsp-webhook\";alg=\"ed25519\";nonce=\"u3Lq0X4pS2m8Vb1cR7tYzA\";keyid=\"gw-2026-11\";expires=${created + 300};created=$created"
        assertIs<Verification.Verified>(verifier().verify("POST", url, signed(reordered)::get, body))
    }

    @Test
    fun `the openfsp-webhook signature is found among other labels`() {
        val headers = signed(params(), label = "openfsp", signatureInput = { "other=(\"@method\");created=$created;tag=\"proxy\", openfsp=$it" }).toMutableMap()
        headers[WebhookHeaders.SIGNATURE] = "other=:AAAA:, " + headers.getValue(WebhookHeaders.SIGNATURE)
        assertIs<Verification.Verified>(verifier().verify("POST", url, headers::get, body))
    }

    @Test
    fun `a header without a label is rejected`() {
        val headers = sign().toMutableMap()
        headers[WebhookHeaders.SIGNATURE_INPUT] = headers.getValue(WebhookHeaders.SIGNATURE_INPUT).removePrefix("sig1=")
        assertEquals("malformed Signature-Input", verifier().verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `a signature under another label than its input is rejected`() {
        val headers = sign().toMutableMap()
        headers[WebhookHeaders.SIGNATURE] = headers.getValue(WebhookHeaders.SIGNATURE).replace("sig1=", "sig2=")
        assertEquals("missing or malformed Signature", verifier().verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `two openfsp-webhook signatures are ambiguous`() {
        val headers = signed(params(), signatureInput = { "sig1=$it, sig2=$it" })
        assertEquals("several openfsp-webhook signatures", verifier().verify("POST", url, headers::get, body).reason())
    }

    @Test
    fun `a signature with another tag is not an openfsp webhook`() {
        assertEquals("no openfsp-webhook signature", verifier().verify("POST", url, signed(params(tag = "other"))::get, body).reason())
    }

    @Test
    fun `other covered components are rejected even when signed`() {
        val components = "(\"@method\" \"@target-uri\" \"content-digest\" \"openfsp-event-id\" \"openfsp-event-type\")"
        assertEquals("covered components differ from the specification", verifier().verify("POST", url, signed(params(components))::get, body).reason())
    }

    @Test
    fun `a Content-Digest with several algorithms is checked on sha-256`() {
        val digest = "sha-512=:${java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-512").digest(body))}:, ${ContentDigest.sha256(body)}"
        assertIs<Verification.Verified>(verifier().verify("POST", url, signed(params(), contentDigest = digest)::get, body))
    }

    @Test
    fun `a Content-Digest without sha-256 is rejected`() {
        val digest = "sha-512=:${java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-512").digest(body))}:"
        assertEquals("Content-Digest has no sha-256 member", verifier().verify("POST", url, signed(params(), contentDigest = digest)::get, body).reason())
    }

    @Test
    fun `a forged signature does not consume the nonce of the genuine delivery`() {
        val headers = sign()
        val forged = headers.toMutableMap()
        forged[WebhookHeaders.SIGNATURE] = "sig1=:${java.util.Base64.getEncoder().encodeToString(ByteArray(64))}:"
        val nonces = InMemoryNonceStore(Clock.fixed(now, ZoneOffset.UTC))
        val verifier = verifier(nonces = nonces)
        assertEquals("invalid signature", verifier.verify("POST", url, forged::get, body).reason())
        assertIs<Verification.Verified>(verifier.verify("POST", url, headers::get, body))
    }

    @Test
    fun `parse reads the openfsp-webhook signature parameters`() {
        val input = sign().getValue(WebhookHeaders.SIGNATURE_INPUT)
        assertEquals(now.epochSecond, SignatureParams.parse("other=(\"@method\");tag=\"proxy\", $input")?.created)
    }
}

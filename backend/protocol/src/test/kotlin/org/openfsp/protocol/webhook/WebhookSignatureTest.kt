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
        assertEquals("invalid validity window", verifier().verify("POST", url, headers::get, body).reason())
    }
}

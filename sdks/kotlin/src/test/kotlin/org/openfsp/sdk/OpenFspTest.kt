package org.openfsp.sdk

import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFspTest {
    private val provider =
        mapOf(
            "id" to "moncash",
            "display_name" to "MonCash",
            "capabilities" to listOf("payments.redirect"),
            "payments" to
                mapOf(
                    "next_actions" to listOf("redirect"),
                    "payer_required" to false,
                    "return_url_required" to true,
                    "expiry_guaranteed" to true,
                    "locales" to listOf("fr"),
                ),
        )

    @Test
    fun `lists providers and keeps them for a minute`() {
        val capabilities = Responses.json(200, mapOf("protocol_version" to "0.1", "providers" to listOf(provider)))
        val clock = FakeClock()
        val transport = FakeTransport(capabilities, capabilities)
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport, clock)

        assertEquals(listOf("MonCash"), openfsp.providers().map { it.displayName })
        assertEquals("MonCash", openfsp.provider("moncash")?.displayName)
        assertNull(openfsp.provider("natcash"))
        assertEquals(1, transport.requests.size)

        clock.advance(Duration.ofSeconds(61))
        openfsp.providers()
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `keeps providers for the lifetime the server announces, up to an hour`() {
        val capabilities = Responses.json(200, mapOf("protocol_version" to "0.1", "providers" to listOf(provider)), mapOf("Cache-Control" to "private, max-age=600"))
        val clock = FakeClock()
        val transport = FakeTransport(capabilities, capabilities)
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport, clock)

        openfsp.providers()
        clock.advance(Duration.ofSeconds(599))
        openfsp.providers()
        assertEquals(1, transport.requests.size)

        clock.advance(Duration.ofSeconds(2))
        openfsp.providers()
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `finds the statement of a provider for a business date`() {
        val statement =
            mapOf(
                "id" to "stm_1",
                "provider" to "moncash",
                "business_date" to "2026-11-16",
                "complete" to true,
                "entries" to
                    listOf(
                        mapOf(
                            "type" to "payment",
                            "amount" to mapOf("amount" to 125000, "currency" to "HTG"),
                            "booked_at" to "2026-11-16T15:04:05Z",
                            "payment" to "pay_1",
                            "match" to "matched",
                        ),
                    ),
                "missing_payments" to emptyList<String>(),
            )
        val transport = FakeTransport(Responses.json(200, mapOf("data" to listOf(statement))), Responses.json(200, mapOf("data" to emptyList<Any>())))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport)

        val found = openfsp.statements.find("moncash", java.time.LocalDate.parse("2026-11-16"))

        assertEquals("pay_1", found?.entries?.single()?.paymentId)
        assertEquals(125000, found?.entries?.single()?.amount?.centimes)
        assertEquals("provider=moncash&business_date=2026-11-16", transport.requests.single().uri.rawQuery)
        assertNull(openfsp.statements.find("moncash", java.time.LocalDate.parse("2026-11-17")))
    }

    @Test
    fun `reads the configuration document without credentials`() {
        val transport =
            FakeTransport(
                Responses.json(
                    200,
                    mapOf(
                        "issuer" to "https://server.example",
                        "protocol_version" to "0.1",
                        "api_base" to "https://server.example/v1",
                        "payments_endpoint" to "https://server.example/v1/payments",
                        "capabilities_endpoint" to "https://server.example/v1/capabilities",
                        "statement_entries_endpoint" to "https://server.example/v1/statement-entries",
                        "capabilities_supported" to listOf("payments", "statements"),
                        "webhook_keys" to listOf(mapOf("kid" to "key-1", "kty" to "OKP", "crv" to "Ed25519", "x" to "AAAA", "openfsp_status" to "active")),
                    ),
                ),
            )
        val configuration = OpenFsp(OpenFspConfig("https://server.example", "k"), transport).configuration()
        assertEquals("https://server.example/v1/statement-entries", configuration.statementEntriesEndpoint)
        assertTrue(configuration.supports("statements"))
        assertEquals(KeyStatus.ACTIVE, configuration.webhookKeys.single().status)
        assertEquals("/.well-known/openfsp-configuration", transport.requests.single().uri.path)
        assertNull(transport.requests.single().header("authorization"))
    }

    @Test
    fun `reads a minimal configuration document`() {
        val minimal = mapOf("issuer" to "https://server.example", "protocol_version" to "0.1", "capabilities_supported" to listOf("payments"))
        val configuration = OpenFsp(OpenFspConfig("https://server.example", "k"), FakeTransport(Responses.json(200, minimal))).configuration()
        assertEquals("0.1", configuration.protocolVersion)
        assertNull(configuration.paymentsEndpoint)
        assertNull(configuration.statementsEndpoint)
        assertTrue(configuration.webhookKeys.isEmpty())
    }

    @Test
    fun `rejects a configuration document without its required members`() {
        val transport = FakeTransport(Responses.json(200, mapOf("issuer" to "https://server.example")))
        val failure = assertFailsWith<ApiException> { OpenFsp(OpenFspConfig("https://server.example", "k"), transport).configuration() }
        assertEquals("unexpected-response", failure.code)
    }

    @Test
    fun `is built from the environment and never shows its key`() {
        val openfsp = OpenFsp.fromEnv(mapOf("OPENFSP_BASE_URL" to "https://server.example", "OPENFSP_API_KEY" to "ofsp_live_secret"))
        assertEquals("OpenFsp(https://server.example/)", openfsp.toString())
        assertFalse("secret" in openfsp.config.toString())
    }
}

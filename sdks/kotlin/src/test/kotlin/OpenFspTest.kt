package org.openfsp.sdk

import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.sdk.model.Fee
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import org.openfsp.sdk.testing.Wire
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openfsp.protocol.Money as WireMoney
import org.openfsp.protocol.api.Fee as WireFee

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
    fun `reads the statement of a provider for a business date`() {
        val withFee = Wire.payment(status = "succeeded", reference = "INV-185").copy(fee = WireFee(WireMoney.htg(2_500), WireFee.PAYER))
        val transport = FakeTransport(Responses.json(200, Wire.statement(listOf(Wire.statementEntry(), Wire.statementEntry(withFee)))), Responses.json(200, Wire.statement(emptyList())))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport)

        val page = openfsp.statements.forDay("moncash", LocalDate.parse("2026-11-15"))

        val entry = page.entries.first()
        assertEquals("pay_test", entry.paymentId)
        assertEquals("INV-184", entry.reference)
        assertEquals(125000, entry.amount.centimes)
        assertEquals(LocalDate.parse("2026-11-15"), entry.businessDate)
        assertNull(entry.fee)
        assertEquals(Fee(Money.centimes(2_500), Fee.Bearer.PAYER), page.entries.last().fee)
        assertFalse(page.hasMore)
        assertEquals("provider=moncash&business_date=2026-11-15", transport.requests.single().uri.rawQuery)
        assertTrue(openfsp.statements.forDay("moncash", LocalDate.parse("2026-11-16")).entries.isEmpty())
    }

    @Test
    fun `follows the cursor of a statement over a range`() {
        val transport = FakeTransport(Responses.json(200, Wire.statement(nextCursor = "next-1")), Responses.json(200, Wire.statement()))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport)
        val from = LocalDate.parse("2026-11-01")
        val to = LocalDate.parse("2026-11-30")

        val first = openfsp.statements.between("moncash", from, to, limit = 1)
        val second = openfsp.statements.between("moncash", from, to, limit = 1, cursor = first.nextCursor)

        assertTrue(first.hasMore)
        assertNull(second.nextCursor)
        assertEquals(
            listOf("provider=moncash&from=2026-11-01&to=2026-11-30&limit=1", "provider=moncash&from=2026-11-01&to=2026-11-30&limit=1&cursor=next-1"),
            transport.requests.map { it.uri.rawQuery },
        )
    }

    @Test
    fun `refuses a statement request the server would refuse before sending it`() {
        val transport = FakeTransport()
        val statements = OpenFsp(OpenFspConfig("https://server.example", "k"), transport).statements
        val day = LocalDate.parse("2026-11-01")

        assertFailsWith<InvalidRequestException> { statements.forDay(" ", day) }
        assertFailsWith<InvalidRequestException> { statements.forDay("moncash", day, limit = 101) }
        assertFailsWith<InvalidRequestException> { statements.between("moncash", day, day.minusDays(1)) }
        assertFailsWith<InvalidRequestException> { statements.between("moncash", day, day.plusDays(31)) }
        assertFailsWith<InvalidRequestException> { statements.forDay("moncash", day, cursor = "") }
        assertTrue(transport.requests.isEmpty())
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
                        "statements_endpoint" to "https://server.example/v1/statements",
                        "conformance_endpoint" to "https://server.example/conformance",
                        "capabilities_supported" to listOf("payments", "statements"),
                        "webhook_keys" to listOf(mapOf("kid" to "key-1", "kty" to "OKP", "crv" to "Ed25519", "x" to "AAAA", "openfsp_status" to "active")),
                    ),
                ),
            )
        val configuration = OpenFsp(OpenFspConfig("https://server.example", "k"), transport).configuration()
        assertEquals("https://server.example/v1/statements", configuration.statementsEndpoint)
        assertEquals("https://server.example/conformance", configuration.conformanceEndpoint)
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

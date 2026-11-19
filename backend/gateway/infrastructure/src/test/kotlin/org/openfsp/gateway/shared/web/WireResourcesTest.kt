package org.openfsp.gateway.shared.web

import org.openfsp.gateway.TestcontainersConfiguration
import org.openfsp.gateway.capabilities.web.CapabilitiesMapper
import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.conformance.ConformanceFixtures
import org.openfsp.gateway.conformance.web.ConformanceResourceMapper
import org.openfsp.gateway.conformance.ControlledPayment
import org.openfsp.gateway.discovery.web.ServiceConfigurationMapper
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.gateway.statement.StatementPage
import org.openfsp.gateway.statements.web.StatementResourceMapper
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.gateway.webhooks.resources.EventBodyMapper
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.webhook.WebhookJwk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "test")
class WireResourcesTest {
    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var payments: PaymentResourceMapper

    @Autowired
    private lateinit var capabilities: CapabilitiesMapper

    @Autowired
    private lateinit var statements: StatementResourceMapper

    @Autowired
    private lateinit var configuration: ServiceConfigurationMapper

    @Autowired
    private lateinit var events: EventBodyMapper

    @Autowired
    private lateinit var conformance: ConformanceResourceMapper

    private fun write(value: Any): String = json.writeValueAsString(value)

    @Test
    fun `a failed payment carries every optional member`() {
        assertEquals(FAILED_PAYMENT_JSON, write(payments.toResource(failed)))
    }

    @Test
    fun `a pending payment omits the absent members`() {
        assertEquals(PENDING_PAYMENT_JSON, write(payments.toResource(pending)))
    }

    @Test
    fun `an event wraps the payment it reports`() {
        assertEquals(EVENT_JSON, String(events.toJson(WebhookEvent("evt_1", "payment.failed", CREATED.plusSeconds(60), 2, PaymentStatus.PENDING, failed))))
    }

    @Test
    fun `a creation event has no previous status`() {
        assertEquals(CREATION_EVENT_JSON, String(events.toJson(WebhookEvent("evt_0", "payment.created", CREATED, 1, null, pending))))
    }

    @Test
    fun `capabilities list each provider with its limits when known`() {
        val described =
            ProviderCapabilities(
                "mock_alpha",
                ProviderDescriptor(
                    displayName = "Alpha",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.REDIRECT, NextAction.Type.PAYER_APPROVAL),
                    payerRequired = true,
                    returnUrlRequired = true,
                    expiryGuaranteed = true,
                    locales = setOf("fr", "en"),
                    logoUrl = URI("https://cdn.example/alpha.svg"),
                ),
                listOf("payments", "payments.statement"),
                AmountLimits(Money(100), Money(1_000_000), Money(5)),
            )
        val plain = ProviderCapabilities("mock_beta", ProviderDescriptor("Beta", setOf(ProviderDescriptor.PAYMENTS), setOf(NextAction.Type.NONE), false), emptyList(), AmountLimits())
        assertEquals(CAPABILITIES_JSON, write(capabilities.toResource(listOf(described, plain))))
    }

    @Test
    fun `a statement page carries its entries and the next cursor`() {
        val page =
            StatementPage(
                listOf(
                    StatementEntry("mock_alpha", LocalDate.parse("2026-11-17"), CREATED, Money(125_000), ProviderFee(Money(500), FeeBearer.MERCHANT), "pay_1", "INV-1"),
                    StatementEntry("mock_alpha", LocalDate.parse("2026-11-17"), CREATED.plusSeconds(1), Money(-2_000), null, "pay_2", "INV-2"),
                ),
                StatementCursor(CREATED.plusSeconds(1), "pay_2"),
            )
        assertEquals(STATEMENT_JSON, write(statements.toResource(page)))
        assertEquals(LAST_STATEMENT_JSON, write(statements.toResource(StatementPage(emptyList(), null))))
    }

    @Test
    fun `the service configuration names every endpoint`() {
        val keys = listOf(WebhookJwk(kid = "key-1", x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo", openfspStatus = "active"))
        assertEquals(CONFIGURATION_JSON, write(configuration.toResource(URI("https://gateway.example/"), listOf("payments.create"), keys, true)))
        assertEquals(CONFIGURATION_WITHOUT_CONFORMANCE_JSON, write(configuration.toResource(URI("https://gateway.example"), emptyList(), emptyList(), false)))
    }

    @Test
    fun `the conformance answers carry the fixtures and the controlled payment`() {
        val fixtures = ConformanceFixtures("ofsp_test_key", CREATED, mapOf("restricted_api_key" to "ofsp_test_other"), URI("https://gateway.example/conformance/control"))
        assertEquals(FIXTURES_JSON, write(conformance.toResource(fixtures)))
        assertEquals(CONTROLLED_JSON, write(conformance.toResource(ControlledPayment("pay_1", true, 2))))
    }

    private companion object {
        val CREATED: Instant = Instant.parse("2026-11-17T10:15:30Z")

        val failed =
            Payment(
                id = "pay_1",
                accountId = "acct_1",
                reference = "INV-1",
                amount = Money(125_000),
                provider = "mock_alpha",
                providerOrderId = "ord-1",
                status = PaymentStatus.FAILED,
                providerReference = "PR-1",
                payerPhoneNumber = "+50934567890",
                description = "Order 1",
                nextAction = NextAction.redirect("https://pay.example/checkout/1", CREATED.plusSeconds(900)),
                metadata = mapOf("cart" to "42"),
                expiresAt = CREATED.plusSeconds(900),
                completedAt = CREATED.plusSeconds(60),
                failureReason = FailureReason.INSUFFICIENT_FUNDS,
                failureDetail = mapOf("provider_code" to "51", "hint" to null, "attempts" to 2),
                createdAt = CREATED,
                updatedAt = CREATED.plusSeconds(60),
                fee = ProviderFee(Money(500), FeeBearer.PAYER),
            )

        val pending = Payment(id = "pay_2", accountId = "acct_1", reference = "INV-2", amount = Money(2_000), provider = "mock_beta", providerOrderId = "ord-2", nextAction = NextAction.payerApproval, createdAt = CREATED)

        const val FAILED_PAYMENT_JSON = """{"id":"pay_1","reference":"INV-1","status":"failed","amount":{"amount":125000,"currency":"HTG"},"provider":"mock_alpha","payer":{"phone_number":"+50934567890"},"description":"Order 1","next_action":{"type":"redirect","redirect_url":"https://pay.example/checkout/1","expires_at":"2026-11-17T10:30:30Z"},"created_at":"2026-11-17T10:15:30Z","updated_at":"2026-11-17T10:16:30Z","expires_at":"2026-11-17T10:30:30Z","completed_at":"2026-11-17T10:16:30Z","failure_reason":"insufficient_funds","failure_detail":{"provider_code":"51","attempts":2},"metadata":{"cart":"42","gateway.order_id":"ord-1","gateway.provider_reference":"PR-1"},"fee":{"amount":{"amount":500,"currency":"HTG"},"bearer":"payer"}}"""
        const val PENDING_PAYMENT_JSON = """{"id":"pay_2","reference":"INV-2","status":"pending","amount":{"amount":2000,"currency":"HTG"},"provider":"mock_beta","next_action":{"type":"payer_approval"},"created_at":"2026-11-17T10:15:30Z","updated_at":"2026-11-17T10:15:30Z","metadata":{"gateway.order_id":"ord-2"}}"""
        const val EVENT_JSON = """{"id":"evt_1","type":"payment.failed","created_at":"2026-11-17T10:16:30Z","sequence":2,"resource_type":"payment","resource_id":"pay_1","reference":"INV-1","previous_status":"pending","data":{"id":"pay_1","reference":"INV-1","status":"failed","amount":{"amount":125000,"currency":"HTG"},"provider":"mock_alpha","payer":{"phone_number":"+50934567890"},"description":"Order 1","next_action":{"type":"redirect","redirect_url":"https://pay.example/checkout/1","expires_at":"2026-11-17T10:30:30Z"},"created_at":"2026-11-17T10:15:30Z","updated_at":"2026-11-17T10:16:30Z","expires_at":"2026-11-17T10:30:30Z","completed_at":"2026-11-17T10:16:30Z","failure_reason":"insufficient_funds","failure_detail":{"provider_code":"51","attempts":2},"metadata":{"cart":"42","gateway.order_id":"ord-1","gateway.provider_reference":"PR-1"},"fee":{"amount":{"amount":500,"currency":"HTG"},"bearer":"payer"}}}"""
        const val CREATION_EVENT_JSON = """{"id":"evt_0","type":"payment.created","created_at":"2026-11-17T10:15:30Z","sequence":1,"resource_type":"payment","resource_id":"pay_2","reference":"INV-2","data":{"id":"pay_2","reference":"INV-2","status":"pending","amount":{"amount":2000,"currency":"HTG"},"provider":"mock_beta","next_action":{"type":"payer_approval"},"created_at":"2026-11-17T10:15:30Z","updated_at":"2026-11-17T10:15:30Z","metadata":{"gateway.order_id":"ord-2"}}}"""
        const val CAPABILITIES_JSON = """{"protocol_version":"0.1.0","providers":[{"id":"mock_alpha","display_name":"Alpha","logo_url":"https://cdn.example/alpha.svg","capabilities":["payments","payments.statement"],"payments":{"next_actions":["redirect","payer_approval"],"payer_required":true,"return_url_required":true,"expiry_guaranteed":true,"limits":{"min_amount":{"amount":100,"currency":"HTG"},"max_amount":{"amount":1000000,"currency":"HTG"},"amount_step":{"amount":5,"currency":"HTG"}},"locales":["en","fr"]}},{"id":"mock_beta","display_name":"Beta","capabilities":[],"payments":{"next_actions":["none"],"payer_required":false,"return_url_required":false,"expiry_guaranteed":false,"locales":[]}}]}"""
        const val STATEMENT_JSON = """{"data":[{"provider":"mock_alpha","business_date":"2026-11-17","booked_at":"2026-11-17T10:15:30Z","amount":{"amount":125000,"currency":"HTG"},"fee":{"amount":{"amount":500,"currency":"HTG"},"bearer":"merchant"},"payment":"pay_1","reference":"INV-1"},{"provider":"mock_alpha","business_date":"2026-11-17","booked_at":"2026-11-17T10:15:31Z","amount":{"amount":-2000,"currency":"HTG"},"payment":"pay_2","reference":"INV-2"}],"next_cursor":"MjAyNi0xMS0xN1QxMDoxNTozMVp8cGF5XzI"}"""
        const val LAST_STATEMENT_JSON = """{"data":[]}"""
        const val CONFIGURATION_JSON = """{"issuer":"https://gateway.example","protocol_version":"0.1.0","api_base":"https://gateway.example/v1","payments_endpoint":"https://gateway.example/v1/payments","capabilities_endpoint":"https://gateway.example/v1/capabilities","statements_endpoint":"https://gateway.example/v1/statements","webhook_keys":[{"kid":"key-1","kty":"OKP","crv":"Ed25519","alg":"EdDSA","use":"sig","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo","openfsp_status":"active"}],"authentication_methods_supported":["bearer"],"scopes_supported":["capabilities:read","payments:read","payments:write","statements:read"],"capabilities_supported":["payments.create"],"webhook_signature_algorithms_supported":["ed25519"],"conformance_endpoint":"https://gateway.example/conformance"}"""
        const val CONFIGURATION_WITHOUT_CONFORMANCE_JSON = """{"issuer":"https://gateway.example","protocol_version":"0.1.0","api_base":"https://gateway.example/v1","payments_endpoint":"https://gateway.example/v1/payments","capabilities_endpoint":"https://gateway.example/v1/capabilities","statements_endpoint":"https://gateway.example/v1/statements","webhook_keys":[],"authentication_methods_supported":["bearer"],"scopes_supported":["capabilities:read","payments:read","payments:write","statements:read"],"capabilities_supported":[],"webhook_signature_algorithms_supported":["ed25519"]}"""
        const val FIXTURES_JSON = """{"api_key":"ofsp_test_key","expires_at":"2026-11-17T10:15:30Z","inputs":{"restricted_api_key":"ofsp_test_other"},"control_endpoint":"https://gateway.example/conformance/control"}"""
        const val CONTROLLED_JSON = """{"payment":"pay_1","settled":true,"callbacks_sent":2}"""
    }
}

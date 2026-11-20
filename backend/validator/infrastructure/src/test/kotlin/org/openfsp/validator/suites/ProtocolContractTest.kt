package org.openfsp.validator.suites

import com.fasterxml.jackson.annotation.JsonInclude
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.api.Fee
import org.openfsp.protocol.api.LimitsResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentRequest
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.protocol.api.StatementEntryResource
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.server.FieldError
import org.openfsp.server.FieldErrorCode
import org.openfsp.server.Problem
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolContractTest {
    private val schemas = NetworkntSchemaCheckers().of(SuiteFiles.load(Path.of(System.getProperty("openfsp.conformance"))).schemas)

    private val json: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .build()

    private val createdAt = Instant.parse("2026-08-17T14:32:07.412Z")
    private val expiresAt = Instant.parse("2026-08-17T15:02:07Z")

    private val pending =
        PaymentResource(
            id = "pay_01J9ZK3QF8XN2M7VYB4C6D8E0G",
            reference = "INV-2026-00184",
            status = "pending",
            amount = Money.htg(125_000),
            provider = "moncash",
            payer = PayerResource("+50934567890"),
            description = "Invoice 184",
            nextAction = NextAction.redirect("https://provider.example/checkout/abc123", expiresAt),
            createdAt = createdAt,
            updatedAt = createdAt,
            expiresAt = expiresAt,
            metadata = mapOf("order_id" to "184"),
        )

    private val succeeded =
        pending.copy(status = "succeeded", nextAction = null, completedAt = createdAt.plusSeconds(240), updatedAt = createdAt.plusSeconds(240))

    private val failed =
        pending.copy(status = "failed", nextAction = null, completedAt = createdAt.plusSeconds(60), failureReason = "insufficient_funds", failureDetail = mapOf("code" to "E4021"))

    @Test
    fun `payment resources match payment and payment-collection`() {
        listOf(pending, succeeded, failed, pending.copy(nextAction = NextAction.payerApproval)).forEach { assertValid("payment", it) }
        assertValid("payment", succeeded.copy(fee = Fee(Money.htg(2_500), Fee.MERCHANT)))
        assertValid("payment", failed.copy(fee = Fee(Money.htg(0), Fee.PAYER)))
        assertValid("payment-collection", PaymentCollection(listOf(succeeded)))
        assertValid("payment-collection", PaymentCollection(emptyList()))
    }

    @Test
    fun `payment events match event`() {
        assertValid("event", event("payment.created", null, pending, 1))
        assertValid("event", event("payment.succeeded", "pending", succeeded, 2))
        assertValid("event", event("payment.failed", "pending", failed, 2))
    }

    @Test
    fun `a pending payment whose creation outcome is unknown has no next_action`() {
        assertValid("payment", pending.copy(nextAction = null))
        assertValid("event", event("payment.created", null, pending.copy(nextAction = null), 1))
    }

    @Test
    fun `problems match problem`() {
        assertValid("problem", Problem.of(ProblemType.PROVIDER_TIMEOUT, "req_01J9ZK3QF8XN2M7VYB4C6D8E0G", instance = "/v1/payments"))
        assertValid(
            "problem",
            Problem.of(
                ProblemType.INVALID_FIELD,
                "req_01J9ZK3QF8XN2M7VYB4C6D8E0H",
                errors = listOf(FieldError("/amount/amount", FieldErrorCode.NOT_POSITIVE), FieldError("/payer/phone_number", FieldErrorCode.MALFORMED, "not E.164")),
            ),
        )
        assertValid(
            "problem",
            Problem.of(ProblemType.AMOUNT_OUT_OF_RANGE, "req_01J9ZR2T4V6X8Z0B2D4F6H8K0M")
                .copy(extensions = mapOf("min_amount" to Money.htg(1_000), "max_amount" to Money.htg(7_500_000), "amount_step" to Money.htg(100))),
        )
        ProblemType.entries.forEach { assertValid("problem", Problem.of(it, "req_${it.code}")) }
    }

    @Test
    fun `capabilities match capabilities`() {
        val payments =
            PaymentsCapabilityResource(
                nextActions = listOf("redirect"),
                payerRequired = false,
                returnUrlRequired = true,
                expiryGuaranteed = false,
                limits = LimitsResource(Money.htg(1_000), Money.htg(7_500_000), Money.htg(100)),
                locales = listOf("fr", "ht"),
            )
        val providers =
            listOf(
                ProviderCapabilitiesResource("moncash", "MonCash", "https://cdn.example/moncash.svg", listOf("payments", "payments.lookup"), payments),
                ProviderCapabilitiesResource("natcash", "NatCash", capabilities = listOf("payments"), payments = payments.copy(limits = null, locales = emptyList())),
            )
        assertValid("capabilities", CapabilitiesResource(ProtocolVersion.CURRENT, providers))
    }

    @Test
    fun `configuration matches discovery`() {
        val base = "https://server.example"
        assertValid(
            "discovery",
            ConfigurationResource(
                issuer = base,
                protocolVersion = ProtocolVersion.CURRENT,
                apiBase = "$base/v1",
                paymentsEndpoint = "$base/v1/payments",
                capabilitiesEndpoint = "$base/v1/capabilities",
                statementsEndpoint = "$base/v1/statements",
                webhookKeys = listOf(WebhookJwk(kid = "whk_06GWCT5DMSTMH111XG1Q28R4MW", x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo", openfspStatus = "active")),
                authenticationMethodsSupported = listOf("bearer"),
                scopesSupported = listOf("capabilities:read", "payments:read", "payments:write", "statements:read"),
                capabilitiesSupported = listOf("payments", "payments.lookup", "payments.statement"),
                webhookSignatureAlgorithmsSupported = listOf("ed25519"),
            ),
        )
    }

    @Test
    fun `statements match statement`() {
        val bookedAt = Instant.parse("2026-09-28T14:33:02Z")
        val day = LocalDate.parse("2026-09-28")
        val known = StatementEntryResource("natcash", day, bookedAt, Money.htg(125_000), payment = "pay_01JAB7C2D9E4F6G8H1J3K5M7N9", reference = "INV-184")
        val reported = StatementEntryResource("natcash", day, bookedAt, Money.htg(125_000), Fee(Money.htg(2_500), Fee.MERCHANT))
        assertValid("statement", StatementCollection(listOf(known, reported), nextCursor = "MjAyNi0wOS0yOFQxNDozMzowMlo"))
        assertValid("statement", StatementCollection(emptyList()))
    }

    @Test
    fun `payment requests match payment-request`() {
        assertValid(
            "payment-request",
            PaymentRequest(
                reference = "INV-2026-00184",
                amount = Money.htg(125_000),
                provider = "mock_openfsp",
                payer = PayerResource("+50934567890"),
                description = "Invoice 184",
                returnUrl = "https://merchant.example/checkout/184/return",
                expiresAt = expiresAt,
                locale = "fr",
                metadata = mapOf("order_id" to "184"),
                callbackUrl = "https://server.example/callbacks/mock_openfsp/token",
            ),
        )
        assertValid("payment-request", PaymentRequest("INV-2026-00185", Money.htg(1), "moncash"))
    }

    private fun event(
        type: String,
        previous: String?,
        payment: PaymentResource,
        sequence: Long,
    ) = PaymentEvent("evt_01J9ZK4T2A0YHQ8F3W6N5R7B1C", type, payment.updatedAt, sequence, PaymentEvent.PAYMENT, payment.id, payment.reference, previous, payment)

    private fun assertValid(
        schema: String,
        value: Any,
    ) {
        val wire: JsonNode = json.readTree(json.writeValueAsString(value))
        assertEquals(emptyList(), schemas.validate(schema, wire), "$schema: $wire")
    }
}

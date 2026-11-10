package org.openfsp.protocol.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.webhook.WebhookJwk
import java.time.Instant
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PayerResource(
    @JsonProperty("phone_number") val phoneNumber: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentRequest(
    val reference: String,
    val amount: Money,
    val provider: String,
    val payer: PayerResource? = null,
    val description: String? = null,
    @JsonProperty("return_url") val returnUrl: String? = null,
    @JsonProperty("expires_at") val expiresAt: Instant? = null,
    val locale: String? = null,
    val metadata: Map<String, String>? = null,
    @JsonProperty("callback_url") val callbackUrl: String? = null,
) {
    companion object {
        val FIELDS = setOf("reference", "amount", "provider", "payer", "description", "return_url", "expires_at", "locale", "metadata", "callback_url")
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentResource(
    val id: String,
    val reference: String,
    val status: String,
    val amount: Money,
    val provider: String,
    @JsonProperty("provider_reference") val providerReference: String? = null,
    @JsonProperty("provider_order_id") val providerOrderId: String,
    val payer: PayerResource? = null,
    val description: String? = null,
    @JsonProperty("next_action") val nextAction: NextAction? = null,
    @JsonProperty("created_at") val createdAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
    @JsonProperty("expires_at") val expiresAt: Instant? = null,
    @JsonProperty("completed_at") val completedAt: Instant? = null,
    @JsonProperty("failure_reason") val failureReason: String? = null,
    @JsonProperty("failure_detail") val failureDetail: Map<String, Any?>? = null,
    val metadata: Map<String, String>? = null,
)

data class PaymentCollection(
    val data: List<PaymentResource>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentEvent(
    val id: String,
    val type: String,
    @JsonProperty("created_at") val createdAt: Instant,
    val sequence: Long,
    @JsonProperty("resource_type") val resourceType: String,
    @JsonProperty("resource_id") val resourceId: String,
    val reference: String,
    @JsonProperty("previous_status") val previousStatus: String? = null,
    val data: PaymentResource,
) {
    companion object {
        const val PAYMENT = "payment"
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LimitsResource(
    @JsonProperty("min_amount") val minAmount: Money? = null,
    @JsonProperty("max_amount") val maxAmount: Money? = null,
    @JsonProperty("amount_step") val amountStep: Money? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaymentsCapabilityResource(
    @JsonProperty("next_actions") val nextActions: List<String>,
    @JsonProperty("payer_required") val payerRequired: Boolean,
    @JsonProperty("return_url_required") val returnUrlRequired: Boolean,
    @JsonProperty("expiry_guaranteed") val expiryGuaranteed: Boolean,
    val limits: LimitsResource? = null,
    val locales: List<String> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProviderCapabilitiesResource(
    val id: String,
    @JsonProperty("display_name") val displayName: String,
    @JsonProperty("logo_url") val logoUrl: String? = null,
    val capabilities: List<String>,
    val payments: PaymentsCapabilityResource,
)

data class CapabilitiesResource(
    @JsonProperty("protocol_version") val protocolVersion: String,
    val providers: List<ProviderCapabilitiesResource>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StatementEntryResource(
    @JsonProperty("provider_reference") val providerReference: String,
    val type: String,
    val amount: Money,
    @JsonProperty("fee_amount") val feeAmount: Money? = null,
    @JsonProperty("net_amount") val netAmount: Money? = null,
    @JsonProperty("booked_at") val bookedAt: Instant,
    @JsonProperty("provider_order_id") val providerOrderId: String? = null,
    val payment: String? = null,
    val match: String,
)

data class StatementResource(
    val id: String,
    val provider: String,
    @JsonProperty("business_date") val businessDate: LocalDate,
    val complete: Boolean,
    val entries: List<StatementEntryResource>,
    @JsonProperty("missing_payments") val missingPayments: List<String>,
)

data class StatementCollection(
    val data: List<StatementResource>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConfigurationResource(
    val issuer: String,
    @JsonProperty("protocol_version") val protocolVersion: String,
    @JsonProperty("api_base") val apiBase: String,
    @JsonProperty("payments_endpoint") val paymentsEndpoint: String,
    @JsonProperty("capabilities_endpoint") val capabilitiesEndpoint: String,
    @JsonProperty("statements_endpoint") val statementsEndpoint: String? = null,
    @JsonProperty("webhook_keys") val webhookKeys: List<WebhookJwk>,
    @JsonProperty("authentication_methods_supported") val authenticationMethodsSupported: List<String> = emptyList(),
    @JsonProperty("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @JsonProperty("capabilities_supported") val capabilitiesSupported: List<String> = emptyList(),
    @JsonProperty("webhook_signature_algorithms_supported") val webhookSignatureAlgorithmsSupported: List<String> = emptyList(),
)

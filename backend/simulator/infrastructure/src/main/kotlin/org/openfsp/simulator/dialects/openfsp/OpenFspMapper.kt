package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.api.ConformanceFixturesResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.StatementEntryResource
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.simulator.common.spi.PublicAddress
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class OpenFspMapper(
    private val profiles: OperatorProfiles,
    private val properties: SimulatorProperties,
    private val address: PublicAddress,
    private val amounts: OpenFspAmounts,
    private val clock: SimulationClock,
) {
    fun issuer(operator: String): String = "${address.baseUrl()}/$operator"

    fun location(
        operator: String,
        paymentId: String,
    ): String = "${issuer(operator)}/v1/payments/$paymentId"

    fun capabilities(profile: OperatorProfile): List<String> =
        listOfNotNull(
            PAYMENTS,
            LOOKUP.takeIf { profile.lookup },
            STATEMENT.takeIf { profile.statements },
            PAYMENT_URL.takeIf { profile.callbacks },
        ).sorted()

    fun toConfiguration(
        profile: OperatorProfile,
        keys: List<WebhookJwk>,
    ): ConfigurationResource {
        val issuer = issuer(profile.id)
        val api = "$issuer/v1"
        return ConfigurationResource(
            issuer = issuer,
            protocolVersion = ProtocolVersion.CURRENT,
            apiBase = api,
            paymentsEndpoint = "$api/payments",
            capabilitiesEndpoint = "$api/capabilities",
            statementsEndpoint = "$api/statements".takeIf { profile.statements },
            webhookKeys = keys,
            authenticationMethodsSupported = listOf(BEARER),
            scopesSupported = SCOPES,
            capabilitiesSupported = capabilities(profile),
            webhookSignatureAlgorithmsSupported = listOf(SignatureParams.ED25519),
            conformanceEndpoint = "$issuer$CONFORMANCE".takeIf { properties.operator(profile.id).credentialOrNull(API_KEY) != null },
        )
    }

    fun toFixtures(profile: OperatorProfile): ConformanceFixturesResource? {
        val credentials = properties.operator(profile.id)
        val apiKey = credentials.credentialOrNull(API_KEY) ?: return null
        val inputs =
            listOfNotNull(
                credentials.credentialOrNull(SECOND_PRINCIPAL_KEY)?.let { RESTRICTED_KEY_INPUT to it },
                apiKey.takeIf { profile.statements }?.let { STATEMENTS_KEY_INPUT to it },
                credentials.credentialOrNull(REVOKED_KEY)?.let { REVOKED_KEY_INPUT to it },
                credentials.credentialOrNull(EXPIRED_KEY)?.let { EXPIRED_KEY_INPUT to it },
            ).toMap()
        return ConformanceFixturesResource(apiKey, clock.now().plus(properties.retention), inputs)
    }

    fun toCapabilities(profile: OperatorProfile): CapabilitiesResource =
        CapabilitiesResource(
            protocolVersion = ProtocolVersion.CURRENT,
            providers =
                listOf(
                    ProviderCapabilitiesResource(
                        id = profile.id,
                        displayName = properties.operator(profile.id).displayName ?: profile.id,
                        capabilities = capabilities(profile),
                        payments =
                            PaymentsCapabilityResource(
                                nextActions = listOf(NextAction.Type.PAYER_APPROVAL.wire, NextAction.Type.REDIRECT.wire),
                                payerRequired = false,
                                returnUrlRequired = false,
                                expiryGuaranteed = false,
                                locales = OpenFspLocales.SUPPORTED,
                            ),
                    ),
                ),
        )

    fun toResource(
        operator: String,
        reported: OperatorPayment,
        stored: SimulatedPayment?,
    ): PaymentResource {
        val status = STATUSES[reported.status] ?: reported.status.lowercase()
        val failed = status == PaymentStatus.FAILED.wire
        val terminal = PaymentStatus.fromWire(status)?.isTerminal == true
        val createdAt = (stored?.createdAt ?: clock.now()).truncatedTo(ChronoUnit.MILLIS)
        val settledAt = stored?.settledAt?.truncatedTo(ChronoUnit.MILLIS)
        val expiresAt = stored?.expiresAt?.truncatedTo(ChronoUnit.SECONDS)
        return PaymentResource(
            id = reported.paymentId,
            reference = reported.reference,
            status = status,
            amount = amounts.toMoney(reported.amount),
            provider = operator,
            payer = stored?.msisdn?.let(::PayerResource),
            description = stored?.description,
            metadata = stored?.metadata,
            nextAction = nextActionOf(operator, reported, stored, expiresAt).takeIf { status == PaymentStatus.PENDING.wire },
            createdAt = createdAt,
            updatedAt = settledAt ?: createdAt,
            expiresAt = expiresAt,
            completedAt = settledAt.takeIf { terminal },
            failureReason = failureReasonOf(reported.reason).takeIf { failed },
            failureDetail = mapOf("code" to (reported.reason ?: UNSPECIFIED), "message" to messageOf(reported.reason)).takeIf { failed },
        )
    }

    fun toEvent(
        operator: String,
        callback: OperatorCallback,
    ): PaymentEvent {
        val reported = OperatorPayment(callback.paymentId, callback.reference, callback.status, callback.amount, callback.reason, null)
        val resource = toResource(operator, reported, callback.payment)
        return PaymentEvent(
            id = "evt_${callback.paymentId}_$TRANSITION_SEQUENCE",
            type = "payment.${resource.status}",
            createdAt = resource.updatedAt,
            sequence = TRANSITION_SEQUENCE,
            resourceType = PaymentEvent.PAYMENT,
            resourceId = resource.id,
            reference = resource.reference,
            previousStatus = PaymentStatus.PENDING.wire,
            data = resource,
        )
    }

    fun toEntry(payment: SimulatedPayment): StatementEntryResource {
        val bookedAt = payment.settledAt!!.truncatedTo(ChronoUnit.SECONDS)
        return StatementEntryResource(
            provider = payment.operator,
            businessDate = clock.businessDate(bookedAt),
            bookedAt = bookedAt,
            amount = amounts.toMoney((payment.reportedAmount ?: payment.amount).toBigDecimal()),
            payment = payment.paymentId,
            reference = payment.reference,
        )
    }

    private fun nextActionOf(
        operator: String,
        reported: OperatorPayment,
        stored: SimulatedPayment?,
        expiresAt: Instant?,
    ): NextAction {
        val hosted = reported.checkoutUrl ?: stored?.takeIf { profiles[operator]?.hostsCheckoutFor(it) == true }?.let { "${address.baseUrl()}/$operator/checkout/${it.paymentId}" }
        return hosted?.let { NextAction.redirect(it, expiresAt) } ?: NextAction.payerApproval
    }

    private fun failureReasonOf(reason: String?): String = (FailureReason.entries.firstOrNull { it.name == reason } ?: FailureReason.UNSPECIFIED).wire

    private fun messageOf(reason: String?): String = MESSAGES[reason] ?: "The payment did not go through"

    companion object {
        const val CONFORMANCE = "/conformance"
        private const val API_KEY = "api-key"
        private const val SECOND_PRINCIPAL_KEY = "second-principal-api-key"
        private const val REVOKED_KEY = "revoked-api-key"
        private const val EXPIRED_KEY = "expired-api-key"
        private const val RESTRICTED_KEY_INPUT = "restricted-key"
        private const val STATEMENTS_KEY_INPUT = "statements-key"
        private const val REVOKED_KEY_INPUT = "revoked-key"
        private const val EXPIRED_KEY_INPUT = "expired-key"
        private const val PAYMENTS = "payments"
        private const val LOOKUP = "payments.lookup"
        private const val STATEMENT = "payments.statement"
        private const val PAYMENT_URL = "webhooks.per_payment_url"
        private const val BEARER = "bearer"
        private const val UNSPECIFIED = "UNSPECIFIED"
        private const val DECLINED = "DECLINED"
        private const val TRANSITION_SEQUENCE = 2L
        private val SCOPES = listOf("capabilities:read", "payments:read", "payments:write", "statements:read")
        private val STATUSES =
            mapOf(
                "PENDING" to PaymentStatus.PENDING.wire,
                "SUCCESSFUL" to PaymentStatus.SUCCEEDED.wire,
                "FAILED" to PaymentStatus.FAILED.wire,
                "EXPIRED" to PaymentStatus.EXPIRED.wire,
            )
        private val MESSAGES =
            mapOf(
                DECLINED to "The payment was declined",
                "INSUFFICIENT_FUNDS" to "The payer balance is insufficient",
                "PAYER_CANCELED" to "The payer canceled the payment",
                "LIMIT_EXCEEDED" to "The payer limit is exceeded",
            )
    }
}

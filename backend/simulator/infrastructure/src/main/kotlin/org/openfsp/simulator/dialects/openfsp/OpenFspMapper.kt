package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.StatementEntryResource
import org.openfsp.protocol.api.StatementResource
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.simulator.common.spi.SpaceAddresses
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.OperatorPayment
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.OperatorStatement
import org.openfsp.simulator.statement.StatementLine
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class OpenFspMapper(
    private val profiles: OperatorProfiles,
    private val properties: SimulatorProperties,
    private val addresses: SpaceAddresses,
    private val amounts: OpenFspAmounts,
    private val clock: Clock,
) {
    fun issuer(
        space: SpaceId,
        operator: String,
    ): String = "${addresses.publicBaseUrl(space)}/$operator"

    fun location(
        space: SpaceId,
        operator: String,
        paymentId: String,
    ): String = "${issuer(space, operator)}/v1/payments/$paymentId"

    fun capabilities(profile: OperatorProfile): List<String> =
        listOfNotNull(
            PAYMENTS,
            LOOKUP.takeIf { profile.lookup },
            STATEMENT.takeIf { profile.statements },
            PAYMENT_URL.takeIf { profile.callbacks },
        ).sorted()

    fun toConfiguration(
        space: SpaceId,
        profile: OperatorProfile,
        keys: List<WebhookJwk>,
    ): ConfigurationResource {
        val issuer = issuer(space, profile.id)
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
        )
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
                                locales = LOCALES,
                            ),
                    ),
                ),
        )

    fun toResource(
        space: SpaceId,
        operator: String,
        reported: OperatorPayment,
        stored: SimulatedPayment?,
    ): PaymentResource {
        val status = STATUSES[reported.status] ?: reported.status.lowercase()
        val failed = status == PaymentStatus.FAILED.wire
        val terminal = PaymentStatus.fromWire(status)?.isTerminal == true
        val createdAt = (stored?.createdAt ?: clock.instant()).truncatedTo(ChronoUnit.MILLIS)
        val settledAt = stored?.settledAt?.truncatedTo(ChronoUnit.MILLIS)
        val expiresAt = stored?.expiresAt?.truncatedTo(ChronoUnit.SECONDS)
        return PaymentResource(
            id = reported.paymentId,
            reference = reported.orderId,
            status = status,
            amount = amounts.toMoney(reported.amount),
            provider = operator,
            payer = stored?.msisdn?.let(::PayerResource),
            description = stored?.description,
            metadata = stored?.metadata,
            nextAction = nextActionOf(space, operator, reported, stored, expiresAt).takeIf { status == PaymentStatus.PENDING.wire },
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
        val reported = OperatorPayment(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason, null)
        val resource = toResource(callback.space, operator, reported, callback.payment)
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

    fun toStatement(
        operator: String,
        statement: OperatorStatement,
    ) = StatementResource(
        id = "stm_${operator}_${statement.businessDate.format(DateTimeFormatter.BASIC_ISO_DATE)}",
        provider = operator,
        businessDate = statement.businessDate,
        complete = statement.closed,
        entries = statement.entries.map(::toEntry),
        missingPayments = emptyList(),
    )

    private fun toEntry(line: StatementLine) =
        StatementEntryResource(
            type = line.type.lowercase().takeIf { it in LINE_TYPES } ?: ADJUSTMENT,
            amount = amounts.toMoney(line.amount),
            bookedAt = LocalDateTime.parse(line.bookedAt).atZone(HAITI).toInstant(),
            payment = line.reference.takeIf { line.orderId != null },
            match = if (line.orderId != null) MATCHED else UNMATCHED,
        )

    private fun nextActionOf(
        space: SpaceId,
        operator: String,
        reported: OperatorPayment,
        stored: SimulatedPayment?,
        expiresAt: Instant?,
    ): NextAction {
        val hosted = reported.checkoutUrl ?: stored?.takeIf { profiles[operator]?.hostsCheckoutFor(it) == true }?.let { "${addresses.publicBaseUrl(space)}/$operator/checkout/${it.paymentId}" }
        return hosted?.let { NextAction.redirect(it, expiresAt) } ?: NextAction.payerApproval
    }

    private fun failureReasonOf(reason: String?): String = (FailureReason.entries.firstOrNull { it.name == reason } ?: FailureReason.UNSPECIFIED).wire

    private fun messageOf(reason: String?): String = MESSAGES[reason] ?: "The payment did not go through"

    private companion object {
        const val PAYMENTS = "payments"
        const val LOOKUP = "payments.lookup"
        const val STATEMENT = "payments.statement"
        const val PAYMENT_URL = "webhooks.per_payment_url"
        const val BEARER = "bearer"
        const val UNSPECIFIED = "UNSPECIFIED"
        const val DECLINED = "DECLINED"
        const val ADJUSTMENT = "adjustment"
        const val MATCHED = "matched"
        const val UNMATCHED = "unmatched"
        const val TRANSITION_SEQUENCE = 2L
        val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")
        val LINE_TYPES = setOf("payment", "reversal", "fee", ADJUSTMENT)
        val LOCALES = listOf("en", "fr", "ht")
        val SCOPES = listOf("capabilities:read", "payments:read", "payments:write", "statements:read")
        val STATUSES =
            mapOf(
                "PENDING" to PaymentStatus.PENDING.wire,
                "SUCCESSFUL" to PaymentStatus.SUCCEEDED.wire,
                "FAILED" to PaymentStatus.FAILED.wire,
                "EXPIRED" to PaymentStatus.EXPIRED.wire,
            )
        val MESSAGES =
            mapOf(
                DECLINED to "The payment was declined",
                "INSUFFICIENT_FUNDS" to "The payer balance is insufficient",
                "PAYER_CANCELED" to "The payer canceled the payment",
                "LIMIT_EXCEEDED" to "The payer limit is exceeded",
            )
    }
}

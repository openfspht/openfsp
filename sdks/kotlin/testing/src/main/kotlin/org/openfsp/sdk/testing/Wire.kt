package org.openfsp.sdk.testing

import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.sdk.model.PaymentEventType
import org.openfsp.sdk.model.WebhookKey
import java.time.Instant

object Wire {
    private const val PENDING = "pending"

    @JvmStatic
    @JvmOverloads
    fun payment(
        status: String = PENDING,
        reference: String = "INV-184",
        amount: Long = 125_000,
        provider: String = "moncash",
    ): PaymentResource =
        PaymentResource(
            id = "pay_test",
            reference = reference,
            status = status,
            amount = Money.htg(amount),
            provider = provider,
            createdAt = Instant.parse("2026-11-15T12:00:00Z"),
            updatedAt = Instant.parse("2026-11-15T12:00:00Z"),
            nextAction = if (status == PENDING) NextAction.payerApproval else null,
            completedAt = if (status == PENDING) null else Instant.parse("2026-11-15T12:01:00Z"),
        )

    @JvmStatic
    @JvmOverloads
    fun event(
        payment: PaymentResource = payment(status = "succeeded"),
        id: String = "evt_test",
        sequence: Long = 2,
    ): PaymentEvent =
        PaymentEvent(
            id = id,
            type = PaymentStatus.fromWire(payment.status)?.let { PaymentEventType.of(it).wire } ?: "payment.${payment.status}",
            createdAt = Instant.parse("2026-11-15T12:01:00Z"),
            sequence = sequence,
            resourceType = PaymentEvent.PAYMENT,
            resourceId = payment.id,
            reference = payment.reference,
            previousStatus = if (payment.status == PENDING) null else PENDING,
            data = payment,
        )

    @JvmStatic
    @JvmOverloads
    fun configuration(
        webhookKeys: List<WebhookKey> = emptyList(),
        issuer: String = "https://server.example",
    ): Map<String, Any> =
        mapOf(
            "issuer" to issuer,
            "protocol_version" to "0.1",
            "capabilities_supported" to listOf("payments"),
            "webhook_keys" to webhookKeys.map(::jwk),
        )

    private fun jwk(key: WebhookKey): Map<String, String> =
        listOfNotNull(
            "kid" to key.keyId,
            "kty" to key.keyType,
            key.curve?.let { "crv" to it },
            key.algorithm?.let { "alg" to it },
            key.use?.let { "use" to it },
            key.x?.let { "x" to it },
            key.status?.let { "openfsp_status" to it.wire },
        ).toMap()
}

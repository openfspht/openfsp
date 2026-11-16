package org.openfsp.sdk.testing

import org.openfsp.protocol.Money
import org.openfsp.protocol.api.PaymentEvent
import org.openfsp.protocol.api.PaymentResource
import java.time.Instant

object Wire {
    @JvmStatic
    @JvmOverloads
    fun payment(
        status: String = "pending",
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
            providerOrderId = "ord_test",
            createdAt = Instant.parse("2026-11-15T12:00:00Z"),
            updatedAt = Instant.parse("2026-11-15T12:00:00Z"),
            completedAt = if (status == "pending") null else Instant.parse("2026-11-15T12:01:00Z"),
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
            type = "payment.${if (payment.status == "pending") "created" else payment.status}",
            createdAt = Instant.parse("2026-11-15T12:01:00Z"),
            sequence = sequence,
            resourceType = PaymentEvent.PAYMENT,
            resourceId = payment.id,
            reference = payment.reference,
            previousStatus = "pending",
            data = payment,
        )
}

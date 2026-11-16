package org.openfsp.sdk

import org.openfsp.protocol.Money as WireMoney
import org.openfsp.protocol.NextAction as WireNextAction
import org.openfsp.protocol.api.LimitsResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.model.NextAction
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.protocol.Mapping
import org.openfsp.sdk.testing.Wire
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MappingTest {
    @Test
    fun `reads a payment in merchant terms`() {
        val payment =
            Mapping.toPayment(
                Wire.payment().copy(
                    payer = PayerResource("+50934567890"),
                    nextAction = WireNextAction.redirect("https://pay.example/checkout/1", Instant.parse("2026-11-15T12:15:00Z")),
                    metadata = mapOf("cart" to "42"),
                ),
            )
        assertEquals(Money.htg(1250), payment.amount)
        assertEquals("+50934567890", payment.payerPhoneNumber)
        assertTrue(payment.requiresRedirect)
        assertEquals("https://pay.example/checkout/1", payment.redirectUrl)
        assertEquals(mapOf("cart" to "42"), payment.metadata)
    }

    @Test
    fun `knows when a payment is done`() {
        assertTrue(Mapping.toPayment(Wire.payment()).isPending)
        val succeeded = Mapping.toPayment(Wire.payment(status = "succeeded"))
        assertTrue(succeeded.isSuccessful && succeeded.isTerminal && !succeeded.requiresRedirect)
        assertEquals(NextAction.None, succeeded.nextAction)
        assertTrue(Mapping.toPayment(Wire.payment().copy(nextAction = WireNextAction.payerApproval)).awaitsPayerApproval)
    }

    @Test
    fun `writes a request without empty fields and checks it first`() {
        val wire = Mapping.toWire(PaymentRequest("INV-184", Money.htg(1250), "moncash", payerPhoneNumber = "+50934567890"))
        assertEquals(WireMoney.htg(125_000), wire.amount)
        assertEquals("+50934567890", wire.payer?.phoneNumber)
        assertEquals(null, wire.metadata)

        val valid = PaymentRequest("INV-184", Money.htg(1), "moncash")
        assertFailsWith<InvalidRequestException> { Mapping.toWire(valid.copy(reference = " ")) }
        assertFailsWith<InvalidRequestException> { Mapping.toWire(valid.copy(amount = Money.centimes(0))) }
        assertFailsWith<InvalidRequestException> { Mapping.toWire(valid.copy(payerPhoneNumber = "34567890")) }
    }

    @Test
    fun `reads a provider and its limits`() {
        val capabilities =
            CapabilitiesResource(
                "0.1",
                listOf(
                    ProviderCapabilitiesResource(
                        id = "moncash",
                        displayName = "MonCash",
                        capabilities = listOf("payments.redirect", "payments.statement"),
                        payments =
                            PaymentsCapabilityResource(
                                nextActions = listOf("redirect"),
                                payerRequired = false,
                                returnUrlRequired = true,
                                expiryGuaranteed = true,
                                limits = LimitsResource(minAmount = WireMoney.htg(100), maxAmount = WireMoney.htg(7_500_000)),
                                locales = listOf("fr", "ht"),
                            ),
                    ),
                ),
            )
        val provider = Mapping.toProviders(capabilities).single()
        assertEquals("MonCash", provider.displayName)
        assertTrue(provider.supports("payments.statement"))
        assertTrue(provider.accepts(Money.htg(1250)))
        assertEquals(false, provider.accepts(Money.htg("0.50")))
        assertEquals(false, provider.accepts(Money.htg(80_000)))
    }
}

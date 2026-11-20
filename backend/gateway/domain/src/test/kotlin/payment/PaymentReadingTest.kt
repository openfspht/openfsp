package org.openfsp.gateway.payment

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingSynchronizationLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openfsp.gateway.webhook.api.RecordPaymentEvents

class PaymentReadingTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val alpha = ProviderStub()
    private val providers = InMemoryProviders(listOf(alpha))
    private val payments = InMemoryPayments()
    private val signals = RecordingOperatorSignals()
    private val limits = RecordingSynchronizationLimits()
    private val settler =
        PaymentSettler(payments, InMemoryPaymentJournal(), RecordPaymentEvents {}, providers, ImmediateTransactions(), signals, CallbackTokens(ByteArray(32)), calendar) { provider, token ->
            URI("https://gateway.example/callbacks/$provider" + (token?.let { "/$it" } ?: ""))
        }
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar, limits)
    private val reader = PaymentReader(payments)

    private fun saved(
        reference: String = "INV-1",
        nextAction: NextAction? = NextAction.payerApproval,
        returnUrl: URI? = URI("https://shop.example/orders/1"),
        account: String = MerchantAccount.DEFAULT,
    ) = payments.save(Payment("pay_$reference", account, reference, Money.htg(1000), "mock_alpha", "order-$reference", nextAction = nextAction, returnUrl = returnUrl, createdAt = calendar.now()))

    @Test
    fun `a payment whose creation is in flight is not found, by id or by reference`() {
        val hidden = saved(nextAction = null)
        assertNull(reader.byReference(MerchantAccount.DEFAULT, "INV-1"))
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { synchronizer.current(MerchantAccount.DEFAULT, hidden.id) }.type)
    }

    @Test
    fun `a payment of another merchant account is not found, by id or by reference`() {
        val foreign = saved(account = "acct_foreign")
        assertNull(reader.byReference(MerchantAccount.DEFAULT, "INV-1"))
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { synchronizer.current(MerchantAccount.DEFAULT, foreign.id) }.type)
        assertEquals(foreign.id, reader.byReference("acct_foreign", "INV-1")?.id)
        assertEquals(foreign.id, synchronizer.current("acct_foreign", foreign.id).id)
    }

    @Test
    fun `a reference is found in any normalization form`() {
        val payment = saved(reference = "INV-é")
        assertEquals(payment.id, reader.byReference(MerchantAccount.DEFAULT, "INV-é")?.id)
    }

    @Test
    fun `a terminal payment is synchronized without lookup nor limit, a pending one within the limit`() {
        val pending = saved()
        alpha.statuses[pending.providerOrderId] = PaymentStatus.PENDING
        alpha.requests += ProviderPaymentRequest(pending.providerOrderId, pending.amount, null, null, URI("https://gateway.example/callbacks"))
        limits.refuseWith = Duration.ofSeconds(2)

        alpha.statuses[pending.providerOrderId] = PaymentStatus.SUCCEEDED
        assertEquals(PaymentStatus.PENDING, synchronizer.current(MerchantAccount.DEFAULT, pending.id).status)

        limits.refuseWith = null
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(MerchantAccount.DEFAULT, pending.id).status)
        limits.refuseWith = Duration.ofSeconds(2)
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(MerchantAccount.DEFAULT, pending.id).status)
        assertEquals(listOf(pending.id), limits.acquired)
    }

    @Test
    fun `the payer returns to the recorded URL, and nowhere for an unreadable return or a payment without return URL`() {
        val payment = saved()
        alpha.statuses[payment.providerOrderId] = PaymentStatus.PENDING
        alpha.requests += ProviderPaymentRequest(payment.providerOrderId, payment.amount, null, null, URI("https://gateway.example/callbacks"))

        assertEquals(payment.returnUrl, synchronizer.returnTarget("mock_alpha", null, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(listOf(payment.id), limits.acquired)
        assertNull(synchronizer.returnTarget("mock_alpha", null, emptyMap()))
        assertNull(synchronizer.returnTarget("unknown", null, mapOf("order_id" to payment.providerOrderId)))
        val silent = saved(reference = "INV-2", returnUrl = null)
        assertNull(synchronizer.returnTarget("mock_alpha", null, mapOf("order_id" to silent.providerOrderId)))
    }

    @Test
    fun `a callback is verified against its own URL and an unverified one is signalled`() {
        val payment = saved()
        assertIs<CallbackOutcome.Unverified>(settler.onSignedCallback("mock_alpha", CallbackRequest({ null }, emptyMap(), ByteArray(0))))
        assertEquals(URI("https://gateway.example/callbacks/mock_alpha"), alpha.callbacks.single().targetUri)
        assertTrue(signals.signals.any { it.first == "mock_alpha" })

        alpha.callbackReport = ProviderReport(payment.providerOrderId, "TX-1", PaymentStatus.SUCCEEDED, payment.amount)
        assertIs<CallbackOutcome.Applied>(settler.onSignedCallback("mock_alpha", CallbackRequest({ null }, emptyMap(), ByteArray(0))))
    }

    @Test
    fun `a verified callback of an unknown event type is acknowledged and leaves the payment unchanged`() {
        val payment = saved()
        alpha.unreportedEventType = "payment.refunded"

        assertIs<CallbackOutcome.Acknowledged>(settler.onSignedCallback("mock_alpha", CallbackRequest({ null }, emptyMap(), ByteArray(0))))
        assertEquals(payment.status, payments.find(payment.id)!!.status)
        assertTrue(signals.signals.none { it.first == "mock_alpha" })
    }

    @Test
    fun `a rejected callback on a payment URL is signalled`() {
        assertNull(settler.onPaymentUrlCallback("mock_alpha", "unknown-token", CallbackRequest({ null }, emptyMap(), ByteArray(0))))
        assertTrue(signals.signals.any { it.first == "mock_alpha" })
    }
}

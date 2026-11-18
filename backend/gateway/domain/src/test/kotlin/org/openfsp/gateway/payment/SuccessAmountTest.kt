package org.openfsp.gateway.payment

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingPaymentEvents
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class SuccessAmountTest {
    private val calendar = BusinessCalendar(TestClock())
    private val stub = ProviderStub()
    private var lookupAmount: Money? = Money.htg(1000)
    private val reporting =
        object : ProviderOperations by stub {
            override val descriptor = stub.descriptor.copy(reportsAmount = true)

            override fun lookup(orderId: String): ProviderReport = stub.lookup(orderId).copy(amount = lookupAmount)
        }
    private val silent = object : ProviderOperations by stub {
        override val id = "mock_silent"
    }
    private val payments = InMemoryPayments()
    private val journal = InMemoryPaymentJournal()
    private val settler =
        PaymentSettler(payments, journal, RecordingPaymentEvents(), InMemoryProviders(listOf(reporting, silent)), ImmediateTransactions(), RecordingOperatorSignals(), CallbackTokens(ByteArray(32)), calendar) { provider, _ ->
            URI("https://gateway.example/callbacks/$provider")
        }

    private fun pending(provider: String = "mock_alpha"): Payment {
        stub.create(ProviderPaymentRequest("order1", Money.htg(1000), null, null, URI("https://gateway.example/callbacks")))
        return payments.save(Payment("pay_1", MerchantAccount.DEFAULT, "ref-1", Money.htg(1000), provider, "order1", nextAction = NextAction.payerApproval, createdAt = calendar.now()))
    }

    private val callback = CallbackRequest(headers = { null }, parameters = emptyMap(), body = ByteArray(0))

    @Test
    fun `a success without amount from a provider that reports amounts leaves the payment pending`() {
        val payment = pending()
        val settled = settler.apply(payment.id, ProviderReport("order1", "TX-1", PaymentStatus.SUCCEEDED, amount = null), TransitionSource.LOOKUP)
        assertEquals(PaymentStatus.PENDING, settled.status)
    }

    @Test
    fun `a signed callback of success without amount is confirmed by a lookup that reports it`() {
        pending()
        stub.statuses["order1"] = PaymentStatus.SUCCEEDED
        stub.callbackReport = ProviderReport("order1", "TX-1", PaymentStatus.SUCCEEDED, amount = null)

        settler.onSignedCallback("mock_alpha", callback)

        assertEquals(PaymentStatus.SUCCEEDED, payments.find("pay_1")!!.status)
        assertEquals(TransitionSource.LOOKUP, journal.transitions("pay_1").single().source)
    }

    @Test
    fun `a confirming lookup without amount still leaves the payment pending`() {
        pending()
        stub.statuses["order1"] = PaymentStatus.SUCCEEDED
        stub.callbackReport = ProviderReport("order1", "TX-1", PaymentStatus.SUCCEEDED, amount = null)
        lookupAmount = null

        settler.onSignedCallback("mock_alpha", callback)

        assertEquals(PaymentStatus.PENDING, payments.find("pay_1")!!.status)
    }

    @Test
    fun `a provider that never reports amounts settles a success without one`() {
        val payment = pending(provider = "mock_silent")
        val settled = settler.apply(payment.id, ProviderReport("order1", "TX-1", PaymentStatus.SUCCEEDED, amount = null), TransitionSource.LOOKUP)
        assertEquals(PaymentStatus.SUCCEEDED, settled.status)
    }
}

package org.openfsp.gateway.payment

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingPaymentEvents
import org.openfsp.gateway.payment.spi.stubs.RecordingSynchronizationLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentSynchronizerTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val alpha = ProviderStub()
    private val lookups = mutableListOf<String>()
    private val counted =
        object : ProviderOperations by alpha {
            override fun lookup(orderId: String): ProviderReport = alpha.lookup(orderId).also { lookups += orderId }
        }
    private val silent =
        ProviderStub(
            id = "mock_silent",
            descriptor =
                ProviderDescriptor(
                    displayName = "Silent",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = false,
                ),
        )
    private val providers = InMemoryProviders(listOf(counted, silent))
    private val payments = InMemoryPayments()
    private val signals = RecordingOperatorSignals()
    private val settler =
        PaymentSettler(payments, InMemoryPaymentJournal(), RecordingPaymentEvents(), providers, ImmediateTransactions(), signals, CallbackTokens(ByteArray(32)), calendar) { provider, _ -> URI("https://gateway.example/callbacks/$provider") }
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar, RecordingSynchronizationLimits())

    private fun pending(
        orderId: String,
        provider: String = "mock_alpha",
        nextAction: NextAction? = NextAction.payerApproval,
    ): Payment {
        val payment = Payment("pay_$orderId", "ref-$orderId", Money.htg(1000), provider, orderId, nextAction = nextAction, createdAt = calendar.now())
        if (provider == "mock_alpha") {
            alpha.create(ProviderPaymentRequest(orderId, payment.amount, null, null, URI("https://gateway.example/callbacks")))
        }
        return payments.save(payment)
    }

    @Test
    fun `payments that stay pending take turns, so a batch never starves the newer ones`() {
        val orders = (1..5).map { "order$it" }
        orders.forEach { pending(it) }
        clock.advance(Duration.ofMinutes(1))

        synchronizer.reconcile(2)
        clock.advance(Duration.ofMinutes(1))
        synchronizer.reconcile(2)
        clock.advance(Duration.ofMinutes(1))
        synchronizer.reconcile(2)

        assertEquals(orders.toSet(), lookups.toSet())
    }

    @Test
    fun `payments at a provider without lookup never fill the batch`() {
        (1..3).forEach { pending("silent$it", provider = "mock_silent") }
        pending("order1")
        clock.advance(Duration.ofMinutes(1))

        assertEquals(1, synchronizer.reconcile(2))
        assertEquals(listOf("order1"), lookups)
    }

    @Test
    fun `a creation still in flight is left alone until its lease runs out`() {
        pending("order1", nextAction = null)
        clock.advance(Duration.ofMinutes(1))
        synchronizer.reconcile(10)
        assertTrue(lookups.isEmpty())

        clock.advance(Payment.CREATION_LEASE)
        synchronizer.reconcile(10)
        assertEquals(listOf("order1"), lookups)
    }

    @Test
    fun `a payment reported terminal by the lookup settles`() {
        val payment = pending("order1")
        alpha.statuses["order1"] = PaymentStatus.SUCCEEDED
        clock.advance(Duration.ofMinutes(1))

        assertEquals(1, synchronizer.reconcile(10))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
    }
}

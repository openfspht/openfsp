package org.openfsp.gateway.statement

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentSettler
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingPaymentEvents
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.gateway.statement.spi.stubs.InMemoryStatementStore
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatementReconcilerTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val epsilon =
        ProviderStub(
            id = "mock_epsilon",
            descriptor =
                ProviderDescriptor(
                    displayName = "Epsilon",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.STATEMENT),
                    nextActions = setOf(NextAction.Type.REDIRECT),
                    payerRequired = false,
                ),
        )
    private val plain =
        ProviderStub(
            id = "mock_plain",
            descriptor = ProviderDescriptor("Plain", setOf(ProviderDescriptor.PAYMENTS), setOf(NextAction.Type.PAYER_APPROVAL), payerRequired = false),
        )
    private val providers = InMemoryProviders(listOf(epsilon, plain))
    private val payments = InMemoryPayments()
    private val signals = RecordingOperatorSignals()
    private val store = InMemoryStatementStore()
    private val settler =
        PaymentSettler(payments, InMemoryPaymentJournal(), RecordingPaymentEvents(), providers, ImmediateTransactions(), signals, CallbackTokens(ByteArray(32)), calendar) { provider, _ ->
            URI("https://gateway.example/callbacks/$provider")
        }
    private val reconciler = StatementReconciler(providers, payments, settler, store, signals, calendar, ImmediateTransactions())

    private fun pending(
        reference: String,
        expiresAt: Instant? = null,
    ) = payments.save(
        Payment("pay_$reference", reference, Money.htg(125_000), "mock_epsilon", "order-$reference", providerReference = "TX-$reference", nextAction = NextAction.payerApproval, expiresAt = expiresAt, createdAt = calendar.now()),
    )

    private fun line(
        reference: String,
        amount: Long = 125_000,
    ) = ProviderStatementLine(reference, null, "payment", Money.htg(amount), bookedAt = calendar.now())

    @Test
    fun `a statement line settles its payment and an unknown line is unmatched and signalled`() {
        val payment = pending("A")
        epsilon.statementLines += line("TX-A")
        epsilon.statementLines += line("TX-unknown", 7_500)

        reconciler.fetch("mock_epsilon", calendar.today())

        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
        val statement = reconciler.operatorStatement("mock_epsilon", calendar.today())!!
        assertEquals(listOf(Match.MATCHED, Match.UNMATCHED), statement.entries.map { it.match })
        assertTrue(signals.signals.any { it.first == "mock_epsilon" })
        assertEquals(listOf("mock_epsilon" to calendar.today()), store.locks)
    }

    @Test
    fun `a line for another amount is a conflict and leaves the payment pending`() {
        val payment = pending("A")
        epsilon.statementLines += line("TX-A", 1_000)
        reconciler.fetch("mock_epsilon", calendar.today())
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
        assertEquals(listOf(Match.CONFLICT), reconciler.operatorStatement("mock_epsilon", calendar.today())!!.entries.map { it.match })
    }

    @Test
    fun `a closed statement expires the unmentioned payments whose expiry passed, and is never fetched again`() {
        val expired = pending("A", expiresAt = calendar.now().plusSeconds(1))
        clock.advance(Duration.ofMinutes(1))
        epsilon.statementClosed = true

        reconciler.fetch("mock_epsilon", calendar.today())
        epsilon.statementLines += line("TX-late")
        reconciler.fetch("mock_epsilon", calendar.today())

        assertEquals(PaymentStatus.EXPIRED, payments.find(expired.id)!!.status)
        assertTrue(reconciler.operatorStatement("mock_epsilon", calendar.today())!!.complete)
        assertTrue(reconciler.operatorStatement("mock_epsilon", calendar.today())!!.entries.isEmpty())
    }

    @Test
    fun `a provider without statement has none, an unknown provider is an invalid field`() {
        assertNull(reconciler.operatorStatement("mock_plain", calendar.today()))
        assertTrue(reconciler.entries(Match.UNMATCHED, "mock_plain", 10, null).entries.isEmpty())
        listOf<() -> Unit>(
            { reconciler.operatorStatement("unknown", calendar.today()) },
            { reconciler.gatewayStatement("unknown", calendar.today()) },
            { reconciler.entries(Match.UNMATCHED, "unknown", 10, null) },
        ).forEach { read -> assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { read() }.type) }
    }
}

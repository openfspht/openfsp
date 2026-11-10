package org.openfsp.gateway.payment

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingPaymentEvents
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentFunctionalTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val alpha = ProviderStub(limits = AmountLimits(minAmount = Money.htg(1000)))
    private val beta =
        ProviderStub(
            id = "mock_beta",
            descriptor =
                ProviderDescriptor(
                    displayName = "Beta",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = false,
                    idempotentCreation = false,
                ),
        )
    private val providers = InMemoryProviders(listOf(alpha, beta))
    private val payments = InMemoryPayments()
    private val journal = InMemoryPaymentJournal()
    private val events = RecordingPaymentEvents()
    private val signals = RecordingOperatorSignals()
    private val transactions = ImmediateTransactions()
    private val tokens = CallbackTokens()
    private val settler = PaymentSettler(payments, journal, events, providers, transactions, signals, tokens, calendar)
    private val creator =
        PaymentCreator(
            providers,
            payments,
            journal,
            events,
            settler,
            transactions,
            signals,
            OrderIdDerivation(ByteArray(32) { 7 }),
            tokens,
            calendar,
        ) { provider, token -> URI("https://gateway.example/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar)

    private fun command(
        reference: String = "INV-1",
        provider: String = "mock_alpha",
        amount: Long = 125_000,
    ) = CreatePaymentCommand(reference, Money.htg(amount), provider)

    @Test
    fun `a created payment is pending, journaled and announced`() {
        val payment = creator.create(command())
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals("STUB-${payment.providerOrderId}", payment.providerReference)
        assertEquals(listOf(TransitionSource.CREATION), journal.transitions(payment.id).map { it.source })
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), events.published)
    }

    @Test
    fun `a reused reference is a conflict and an amount below the bounds never reaches the provider`() {
        creator.create(command())
        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command()) }.type)
        val calls = alpha.requests.size
        assertEquals(ProblemType.AMOUNT_OUT_OF_RANGE, assertFailsWith<OpenFspException> { creator.create(command("INV-2", amount = 999)) }.type)
        assertEquals(calls, alpha.requests.size)
    }

    @Test
    fun `a terminal payment never moves and a contradiction is recorded`() {
        val payment = creator.create(command())
        val success = ProviderReport(payment.providerOrderId, null, PaymentStatus.SUCCEEDED, payment.amount)
        assertEquals(PaymentStatus.SUCCEEDED, settler.apply(payment.id, success, TransitionSource.SIGNED_CALLBACK).status)
        val failure = success.copy(status = PaymentStatus.FAILED)
        assertEquals(PaymentStatus.SUCCEEDED, settler.apply(payment.id, failure, TransitionSource.LOOKUP).status)
        assertEquals(1, journal.conflicts(payment.id).size)
        assertTrue(signals.signals.any { it.first == payment.id })
    }

    @Test
    fun `a success for another amount keeps the payment pending`() {
        val payment = creator.create(command())
        val wrong = ProviderReport(payment.providerOrderId, null, PaymentStatus.SUCCEEDED, Money.htg(1))
        assertEquals(PaymentStatus.PENDING, settler.apply(payment.id, wrong, TransitionSource.STATEMENT).status)
        assertEquals(1, journal.conflicts(payment.id).size)
    }

    @Test
    fun `a failure without reason reads as unspecified`() {
        val payment = creator.create(command())
        val failed = settler.apply(payment.id, ProviderReport(payment.providerOrderId, null, PaymentStatus.FAILED, payment.amount), TransitionSource.LOOKUP)
        assertEquals(org.openfsp.protocol.FailureReason.UNSPECIFIED, failed.failureReason)
        assertNull(failed.nextAction)
    }

    @Test
    fun `an unknown outcome at a provider without idempotency keeps the payment pending and signals it`() {
        beta.failure = ProviderFailure.Timeout()
        assertEquals(ProblemType.PROVIDER_TIMEOUT, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_beta")) }.type)
        val payment = payments.withReference("INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals(NextAction.payerApproval, payment.nextAction)
        assertEquals(1, journal.indeterminate(payment.id).size)
        assertTrue(signals.signals.any { it.first == payment.id })
    }

    @Test
    fun `an unreachable provider without idempotency leaves nothing behind`() {
        beta.failure = ProviderFailure.Unavailable()
        assertEquals(ProblemType.PROVIDER_UNAVAILABLE, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_beta")) }.type)
        assertNull(payments.withReference("INV-1"))
    }

    @Test
    fun `a payment still pending after its expiry expires at the next lookup`() {
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        clock.advance(Duration.ofMinutes(2))
        assertEquals(PaymentStatus.EXPIRED, synchronizer.synchronize(payment.id).status)
    }

    @Test
    fun `synchronizing without lookup is not supported`() {
        val payment = creator.create(command(provider = "mock_beta"))
        assertEquals(ProblemType.CAPABILITY_NOT_SUPPORTED, assertFailsWith<OpenFspException> { synchronizer.synchronize(payment.id) }.type)
    }

    @Test
    fun `an attestation settles the payment and is recorded`() {
        val payment = creator.create(command(provider = "mock_beta"))
        val attested = settler.attest(payment.id, Attestation("ops@merchant.example", PaymentStatus.SUCCEEDED, "statement line 12"))
        assertEquals(PaymentStatus.SUCCEEDED, attested.status)
        assertEquals(TransitionSource.ATTESTATION, journal.transitions(payment.id).last().source)
        assertEquals(1, journal.attestations.size)
    }
}

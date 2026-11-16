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
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Effect
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
    private val gamma =
        ProviderStub(
            id = "mock_gamma",
            descriptor =
                ProviderDescriptor(
                    displayName = "Gamma",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.REDIRECT),
                    payerRequired = false,
                    idempotentCreation = false,
                ),
        )
    private val providers = InMemoryProviders(listOf(alpha, beta, gamma))
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
    fun `the published metadata adds the gateway keys without replacing a client key`() {
        val payment = creator.create(command().copy(metadata = mapOf("order" to "184", Payment.ORDER_ID_KEY to "mine")))
        assertEquals(mapOf("order" to "184", Payment.ORDER_ID_KEY to "mine", Payment.PROVIDER_REFERENCE_KEY to payment.providerReference), payment.publishedMetadata)
        val plain = creator.create(command(reference = "INV-2"))
        assertEquals(mapOf(Payment.ORDER_ID_KEY to plain.providerOrderId, Payment.PROVIDER_REFERENCE_KEY to plain.providerReference), plain.publishedMetadata)
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
    fun `a terminal payment reported again in its state for another amount is a conflict`() {
        val payment = creator.create(command())
        val success = ProviderReport(payment.providerOrderId, null, PaymentStatus.SUCCEEDED, payment.amount)
        settler.apply(payment.id, success, TransitionSource.SIGNED_CALLBACK)
        val settled = settler.apply(payment.id, success.copy(amount = Money.htg(1)), TransitionSource.STATEMENT)
        assertEquals(payment.amount, settled.amount)
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
    fun `a success reported without amount settles, since the amount check needs a reported amount`() {
        val payment = creator.create(command())
        val unknownAmount = ProviderReport(payment.providerOrderId, "TX-1", PaymentStatus.SUCCEEDED, null)
        assertEquals(PaymentStatus.SUCCEEDED, settler.apply(payment.id, unknownAmount, TransitionSource.LOOKUP).status)
        assertEquals(0, journal.conflicts(payment.id).size)
    }

    @Test
    fun `a failure without reason reads as unspecified`() {
        val payment = creator.create(command())
        val failed = settler.apply(payment.id, ProviderReport(payment.providerOrderId, null, PaymentStatus.FAILED, payment.amount), TransitionSource.LOOKUP)
        assertEquals(org.openfsp.protocol.FailureReason.UNSPECIFIED, failed.failureReason)
        assertNull(failed.nextAction)
    }

    @Test
    fun `an unknown outcome at a provider without idempotency keeps the payment pending without next action and signals it`() {
        beta.failure = ProviderFailure.Timeout()
        assertEquals(ProblemType.PROVIDER_TIMEOUT, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_beta")) }.type)
        val payment = payments.withReference("INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertNull(payment.nextAction)
        assertTrue(payment.creationOutcomeUnknown)
        assertTrue(payment.isVisible)
        assertEquals(1, journal.indeterminate(payment.id).size)
        assertTrue(signals.signals.any { it.first == payment.id })
    }

    @Test
    fun `a payment of unknown creation outcome is visible and announced once as created`() {
        gamma.failure = ProviderFailure.Timeout()
        assertEquals(ProblemType.PROVIDER_TIMEOUT, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_gamma")) }.type)
        val payment = payments.withReference("INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertNull(payment.nextAction)
        assertTrue(payment.isVisible)
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), events.published)
    }

    @Test
    fun `a pending payment is hidden only while its creation is in flight and a terminal one is always visible`() {
        val payment = creator.create(command())
        assertTrue(payment.isVisible)
        val inFlight = payment.copy(nextAction = null)
        assertTrue(inFlight.isCreationInFlight)
        assertTrue(!inFlight.isVisible)
        assertTrue(inFlight.withUnknownCreationOutcome().isVisible)
        val failed = settler.apply(payment.id, ProviderReport(payment.providerOrderId, null, PaymentStatus.FAILED, payment.amount), TransitionSource.LOOKUP)
        assertTrue(failed.isVisible)
    }

    @Test
    fun `the provisional payment is hidden while the provider call is in flight`() {
        val seen =
            object : ProviderOperations by beta {
                var during: Payment? = null

                override fun create(request: ProviderPaymentRequest) = beta.create(request).also { during = payments.withReference("INV-1") }
            }
        val inFlightCreator =
            PaymentCreator(
                InMemoryProviders(listOf(seen)),
                payments,
                journal,
                events,
                settler,
                transactions,
                signals,
                OrderIdDerivation(ByteArray(32) { 7 }),
                tokens,
                calendar,
            ) { provider, _ -> URI("https://gateway.example/callbacks/$provider") }
        val payment = inFlightCreator.create(command(provider = "mock_beta"))
        assertTrue(!seen.during!!.isVisible)
        assertTrue(payment.isVisible)
    }

    @Test
    fun `an unknown outcome at an idempotent provider keeps the payment pending, visible and signaled`() {
        alpha.failure = ProviderFailure.Timeout()
        assertEquals(ProblemType.PROVIDER_TIMEOUT, assertFailsWith<OpenFspException> { creator.create(command()) }.type)
        val payment = payments.withReference("INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertTrue(payment.isVisible)
        assertNull(payment.nextAction)
        assertEquals(1, journal.indeterminate(payment.id).size)
        assertTrue(signals.signals.any { it.first == payment.id })
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), events.published)
    }

    @Test
    fun `a second unknown outcome on retry announces the payment no more`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = ProviderFailure.Rejected("The provider answered 500")
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertEquals(2, alpha.requests.size)
        assertEquals(1, events.published.size)
    }

    @Test
    fun `a retry after an unknown outcome settles from the lookup without creating again`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = null
        val provisional = payments.withReference("INV-1")!!
        alpha.statuses[provisional.providerOrderId] = PaymentStatus.SUCCEEDED

        val payment = creator.create(command())
        assertEquals(provisional.id, payment.id)
        assertEquals(PaymentStatus.SUCCEEDED, payment.status)
        assertEquals(1, alpha.requests.size)
    }

    @Test
    fun `a retry after an unknown outcome creates again at an idempotent provider once the lookup finds it pending`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = null
        val provisional = payments.withReference("INV-1")!!
        alpha.statuses[provisional.providerOrderId] = PaymentStatus.PENDING

        val payment = creator.create(command())
        assertEquals(provisional.id, payment.id)
        assertEquals(NextAction.payerApproval, payment.nextAction)
        assertTrue(!payment.creationOutcomeUnknown)
        assertEquals(2, alpha.requests.size)
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), events.published)
    }

    @Test
    fun `a retry for another amount on a payment of unknown outcome is a reference conflict`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = null
        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command(amount = 130_000)) }.type)
        assertEquals(1, alpha.requests.size)
    }

    @Test
    fun `a retry after an unknown outcome at a provider without idempotency is never resent`() {
        gamma.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_gamma")) }
        gamma.failure = null
        assertEquals(ProblemType.PROVIDER_ERROR, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_gamma")) }.type)
        assertEquals(1, gamma.requests.size)
    }

    @Test
    fun `a provider rejection without effect reports no effect, relays the provider code in the detail and leaves nothing behind`() {
        alpha.failure = ProviderFailure.Rejected("The provider answered 422", mapOf("code" to "invalid-field"), withoutEffect = true)
        val failure = assertFailsWith<OpenFspException> { creator.create(command()) }
        assertEquals(ProblemType.PROVIDER_ERROR, failure.type)
        assertEquals(Effect.NONE, failure.effect)
        assertEquals("invalid-field", failure.detail)
        assertNull(payments.withReference("INV-1"))
        assertTrue(signals.signals.isEmpty())
    }

    @Test
    fun `a provider error keeps the outcome unknown and the payment pending`() {
        alpha.failure = ProviderFailure.Rejected("The provider answered 500")
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertTrue(payments.withReference("INV-1") != null)
    }

    @Test
    fun `a rejection without effect after an unknown outcome keeps the outcome unknown`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = ProviderFailure.Rejected("The provider answered 422", withoutEffect = true)
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertTrue(payments.withReference("INV-1") != null)
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
    fun `a failed lookup after the expiry never expires the payment`() {
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        clock.advance(Duration.ofMinutes(2))
        listOf(ProviderFailure.Rejected("MonCash answered 401"), ProviderFailure.Unavailable(retryAfter = Duration.ofSeconds(30)), ProviderFailure.Timeout()).forEach { failure ->
            alpha.failure = failure
            assertFailsWith<OpenFspException> { synchronizer.synchronize(payment.id) }
            assertEquals(0, synchronizer.reconcile(10))
        }
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
    }

    @Test
    fun `a lookup sent before the expiry keeps the payment pending even when the answer arrives after it`() {
        val slowAlpha =
            object : ProviderOperations by alpha {
                override fun lookup(orderId: String): ProviderReport = alpha.lookup(orderId).also { clock.advance(Duration.ofMinutes(2)) }
            }
        val slowSynchronizer = PaymentSynchronizer(payments, InMemoryProviders(listOf(slowAlpha, beta)), settler, signals, calendar)
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        assertEquals(PaymentStatus.PENDING, slowSynchronizer.synchronize(payment.id).status)
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

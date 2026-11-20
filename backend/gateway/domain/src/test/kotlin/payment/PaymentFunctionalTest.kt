package org.openfsp.gateway.payment

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.idempotency.IdempotentExecutions
import org.openfsp.gateway.payment.spi.CallbackUrls
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingSynchronizationLimits
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Effect
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.server.OrderIdDerivation
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openfsp.gateway.webhook.api.RecordPaymentEvents

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
    private val delta =
        ProviderStub(
            id = "mock_delta",
            descriptor =
                ProviderDescriptor(
                    displayName = "Delta",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = false,
                    signedCallbacks = true,
                    paymentUrl = true,
                ),
        )
    private val epsilon = ProviderStub(id = "mock_epsilon", descriptor = alpha.descriptor.copy(displayName = "Epsilon", expiryGuaranteed = true))
    private val providers = InMemoryProviders(listOf(alpha, beta, gamma, delta, epsilon))
    private val payments = InMemoryPayments()
    private val journal = InMemoryPaymentJournal()
    private val published = mutableListOf<PaymentTransitioned>()
    private val events = RecordPaymentEvents { published += it }
    private val signals = RecordingOperatorSignals()
    private val transactions = ImmediateTransactions()
    private val tokens = CallbackTokens(ByteArray(32) { 9 })
    private val callbackUrls = CallbackUrls { provider, token -> URI("https://gateway.example/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
    private val settler = PaymentSettler(payments, journal, events, providers, transactions, signals, tokens, calendar, callbackUrls)
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
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar, RecordingSynchronizationLimits())

    private fun creatorWith(vararg operations: ProviderOperations) =
        PaymentCreator(
            InMemoryProviders(operations.toList()),
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

    private fun command(
        reference: String = "INV-1",
        provider: String = "mock_alpha",
        amount: Long = 125_000,
    ) = CreatePaymentCommand(MerchantAccount.DEFAULT, reference, Money.htg(amount), provider)

    @Test
    fun `a created payment is pending, journaled and announced`() {
        val payment = creator.create(command())
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals("STUB-${payment.providerOrderId}", payment.providerReference)
        assertEquals(listOf(TransitionSource.CREATION), journal.transitions(payment.id).map { it.source })
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), published)
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
    fun `a fee reported with the terminal state is recorded and a later report without it never erases it`() {
        val payment = creator.create(command())
        val fee = ProviderFee(Money.htg(2_500), FeeBearer.MERCHANT)
        val pending = ProviderReport(payment.providerOrderId, null, PaymentStatus.PENDING, payment.amount, fee = fee)
        assertNull(settler.apply(payment.id, pending, TransitionSource.LOOKUP).fee)
        val success = pending.copy(status = PaymentStatus.SUCCEEDED)
        assertEquals(fee, settler.apply(payment.id, success, TransitionSource.SIGNED_CALLBACK).fee)
        assertEquals(fee, settler.apply(payment.id, success.copy(fee = null), TransitionSource.LOOKUP).fee)
        assertEquals(fee, settler.apply(payment.id, success.copy(fee = ProviderFee(Money.htg(1), FeeBearer.PAYER)), TransitionSource.LOOKUP).fee)
        assertEquals(0, journal.conflicts(payment.id).size)
        assertEquals(2, published.size)
    }

    @Test
    fun `a payment settles without a fee, never a computed one, and takes the fee its provider reports afterwards`() {
        val payment = creator.create(command())
        val success = ProviderReport(payment.providerOrderId, null, PaymentStatus.SUCCEEDED, payment.amount)
        val settled = settler.apply(payment.id, success, TransitionSource.SIGNED_CALLBACK)
        assertEquals(PaymentStatus.SUCCEEDED, settled.status)
        assertNull(settled.fee)
        val zero = ProviderFee(Money.htg(0), FeeBearer.PAYER)
        val completed = settler.apply(payment.id, success.copy(fee = zero), TransitionSource.LOOKUP)
        assertEquals(zero, completed.fee)
        assertEquals(settled.completedAt, completed.completedAt)
        assertEquals(2, published.size)
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
        val payment = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
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
        val payment = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertNull(payment.nextAction)
        assertTrue(payment.isVisible)
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), published)
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

                override fun create(request: ProviderPaymentRequest) = beta.create(request).also { during = payments.withReference(MerchantAccount.DEFAULT, "INV-1") }
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
        val payment = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertTrue(payment.isVisible)
        assertNull(payment.nextAction)
        assertEquals(1, journal.indeterminate(payment.id).size)
        assertTrue(signals.signals.any { it.first == payment.id })
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), published)
    }

    @Test
    fun `a second unknown outcome on retry announces the payment no more`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = ProviderFailure.Rejected("The provider answered 500")
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertEquals(2, alpha.requests.size)
        assertEquals(1, published.size)
    }

    @Test
    fun `a retry after an unknown outcome settles from the lookup without creating again`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = null
        val provisional = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
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
        val provisional = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        alpha.statuses[provisional.providerOrderId] = PaymentStatus.PENDING

        val payment = creator.create(command())
        assertEquals(provisional.id, payment.id)
        assertEquals(NextAction.payerApproval, payment.nextAction)
        assertTrue(!payment.creationOutcomeUnknown)
        assertEquals(2, alpha.requests.size)
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), published)
    }

    @Test
    fun `a resent creation repeats the callback URL of the first attempt, whose token still authenticates the payment`() {
        delta.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_delta")) }
        delta.failure = null
        val provisional = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        delta.statuses[provisional.providerOrderId] = PaymentStatus.PENDING

        val payment = creator.create(command(provider = "mock_delta"))
        val (first, resent) = delta.requests
        assertEquals(first, resent)
        assertTrue(tokens.matches(resent.callbackUrl.path.substringAfterLast('/'), payment.callbackTokenDigest))
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
    fun `the expiry the merchant asks for reaches the provider and the payment keeps only the expiry the provider guarantees`() {
        val expiresAt = clock.instant().plus(Duration.ofMinutes(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        val payment = creator.create(command().copy(expiresAt = expiresAt))
        assertEquals(expiresAt, alpha.requests.last().expiresAt)
        assertNull(payment.expiresAt)
    }

    @Test
    fun `a retry for another expiry or locale on a payment of unknown outcome is a reference conflict`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command().copy(locale = "fr")) }
        alpha.failure = null
        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command().copy(locale = "en")) }.type)
        val later = clock.instant().plus(Duration.ofHours(1))
        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command().copy(locale = "fr", expiresAt = later)) }.type)
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
        assertNull(payments.withReference(MerchantAccount.DEFAULT, "INV-1"))
        assertTrue(signals.signals.isEmpty())
    }

    @Test
    fun `a provider error keeps the outcome unknown and the payment pending`() {
        alpha.failure = ProviderFailure.Rejected("The provider answered 500")
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertTrue(payments.withReference(MerchantAccount.DEFAULT, "INV-1") != null)
    }

    @Test
    fun `a rejection without effect after an unknown outcome keeps the outcome unknown`() {
        alpha.failure = ProviderFailure.Timeout()
        assertFailsWith<OpenFspException> { creator.create(command()) }
        alpha.failure = ProviderFailure.Rejected("The provider answered 422", withoutEffect = true)
        assertEquals(Effect.UNKNOWN, assertFailsWith<OpenFspException> { creator.create(command()) }.effect)
        assertTrue(payments.withReference(MerchantAccount.DEFAULT, "INV-1") != null)
    }

    @Test
    fun `an unreachable provider without idempotency leaves nothing behind`() {
        beta.failure = ProviderFailure.Unavailable()
        assertEquals(ProblemType.PROVIDER_UNAVAILABLE, assertFailsWith<OpenFspException> { creator.create(command(provider = "mock_beta")) }.type)
        assertNull(payments.withReference(MerchantAccount.DEFAULT, "INV-1"))
    }

    @Test
    fun `an unreachable provider never forgets a payment whose outcome another attempt made unknown`() {
        val racing =
            object : ProviderOperations by beta {
                override fun create(request: ProviderPaymentRequest): ProviderPayment {
                    val provisional = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
                    journal.record(IndeterminateOperation(provisional.id, "create", "timeout", clock.instant()))
                    payments.save(provisional.withUnknownCreationOutcome())
                    throw ProviderFailure.Unavailable()
                }
            }
        val racingCreator = creatorWith(racing)
        assertEquals(ProblemType.PROVIDER_UNAVAILABLE, assertFailsWith<OpenFspException> { racingCreator.create(command(provider = "mock_beta")) }.type)
        val kept = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        assertTrue(kept.creationOutcomeUnknown)
        assertEquals(1, journal.indeterminate(kept.id).size)
    }

    @Test
    fun `a resend under another key while the first creation waits upstream is in progress and calls nobody`() {
        var resent: OpenFspException? = null
        val waiting =
            object : ProviderOperations by alpha {
                override fun create(request: ProviderPaymentRequest): ProviderPayment {
                    resent = assertFailsWith<OpenFspException> { creatorWith(alpha).create(command().copy(request = CreationRequest("tests", "key-2", "same"))) }
                    return alpha.create(request)
                }
            }
        val payment = creatorWith(waiting).create(command().copy(request = CreationRequest("tests", "key-1", "same")))

        assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, resent!!.type)
        assertTrue(resent.retryAfter != null)
        assertEquals(1, alpha.requests.size)
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING)), published)
        assertTrue(journal.indeterminate(payment.id).isEmpty())
    }

    @Test
    fun `the same request resent after its creation returns the payment, another request under the reference is a conflict`() {
        val request = CreationRequest("tests", "key-1", "same")
        val payment = creator.create(command().copy(request = request))

        assertEquals(payment, creator.create(command().copy(request = request)))
        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command().copy(request = request.copy(idempotencyKey = "key-2"))) }.type)
        assertEquals(1, alpha.requests.size)
    }

    @Test
    fun `the same request resent after the idempotency retention is a reference conflict`() {
        val request = CreationRequest("tests", "key-1", "same")
        creator.create(command().copy(request = request))
        clock.advance(IdempotentExecutions.RETENTION.plusSeconds(1))

        assertEquals(ProblemType.REFERENCE_CONFLICT, assertFailsWith<OpenFspException> { creator.create(command().copy(request = request)) }.type)
        assertEquals(1, alpha.requests.size)
    }

    @Test
    fun `an upstream creation still in progress is in progress here, without an unknown outcome`() {
        alpha.failure = ProviderFailure.InProgress(Duration.ofSeconds(2))
        val refused = assertFailsWith<OpenFspException> { creator.create(command()) }
        assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, refused.type)
        assertEquals(Duration.ofSeconds(2), refused.retryAfter)
        val provisional = payments.withReference(MerchantAccount.DEFAULT, "INV-1")!!
        assertTrue(provisional.isCreationInFlight)
        assertTrue(published.isEmpty())
        assertTrue(journal.indeterminate(provisional.id).isEmpty())
    }

    @Test
    fun `a creation left in flight past its lease is resumed`() {
        val request = CreationRequest("tests", "key-1", "same")
        alpha.failure = ProviderFailure.InProgress(null)
        assertFailsWith<OpenFspException> { creator.create(command().copy(request = request)) }
        alpha.failure = null
        assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, assertFailsWith<OpenFspException> { creator.create(command().copy(request = request)) }.type)

        clock.advance(Payment.CREATION_LEASE.plusSeconds(1))
        val payment = creator.create(command().copy(request = request))
        assertEquals(NextAction.payerApproval, payment.nextAction)
        assertEquals(2, alpha.requests.size)
    }

    @Test
    fun `two first attempts at the same instant leave one creation upstream`() {
        val racing =
            object : Payments by payments {
                var hidden = true

                override fun withReference(
                    account: String,
                    reference: String,
                ): Payment? = if (hidden) null.also { hidden = false } else payments.withReference(account, reference)
            }
        payments.save(
            Payment("pay_first", MerchantAccount.DEFAULT, "INV-1", Money.htg(125_000), "mock_alpha", "order-first", createdAt = clock.instant(), creationRequest = CreationRequest("tests", "key-1", "same")),
        )
        val second =
            PaymentCreator(providers, racing, journal, events, settler, transactions, signals, OrderIdDerivation(ByteArray(32) { 7 }), tokens, calendar) { provider, _ ->
                URI("https://gateway.example/callbacks/$provider")
            }
        assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, assertFailsWith<OpenFspException> { second.create(command().copy(request = CreationRequest("tests", "key-2", "same"))) }.type)
        assertTrue(alpha.requests.isEmpty())
    }

    @Test
    fun `a payment still pending after its expiry stays pending while the provider reports it pending`() {
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        clock.advance(Duration.ofMinutes(2))
        assertEquals(PaymentStatus.PENDING, synchronizer.current(MerchantAccount.DEFAULT, payment.id).status)
        assertEquals(1, synchronizer.reconcile(10))
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
    }

    @Test
    fun `a payment still pending after its expiry expires at the next lookup when the provider guarantees expiry`() {
        val payment = creator.create(command(provider = "mock_epsilon"))
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        clock.advance(Duration.ofMinutes(2))
        assertEquals(PaymentStatus.EXPIRED, synchronizer.current(MerchantAccount.DEFAULT, payment.id).status)
    }

    @Test
    fun `a failed lookup after the expiry never expires the payment`() {
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        clock.advance(Duration.ofMinutes(2))
        listOf(ProviderFailure.Rejected("MonCash answered 401"), ProviderFailure.Unavailable(retryAfter = Duration.ofSeconds(30)), ProviderFailure.Timeout()).forEach { failure ->
            alpha.failure = failure
            assertEquals(PaymentStatus.PENDING, synchronizer.current(MerchantAccount.DEFAULT, payment.id).status)
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
        val slowSynchronizer = PaymentSynchronizer(payments, InMemoryProviders(listOf(slowAlpha, beta)), settler, signals, calendar, RecordingSynchronizationLimits())
        val payment = creator.create(command())
        payments.save(payment.copy(expiresAt = clock.instant().plusSeconds(60)))
        assertEquals(PaymentStatus.PENDING, slowSynchronizer.current(MerchantAccount.DEFAULT, payment.id).status)
    }

    @Test
    fun `reading a payment of a provider without lookup returns its known state`() {
        val payment = creator.create(command(provider = "mock_beta"))
        assertEquals(PaymentStatus.PENDING, synchronizer.current(MerchantAccount.DEFAULT, payment.id).status)
    }

    @Test
    fun `an attestation on a terminal payment is a state conflict and records nothing`() {
        val payment = creator.create(command(provider = "mock_beta"))
        settler.attest(payment.id, Attestation("ops@merchant.example", PaymentStatus.SUCCEEDED, "statement line 12"))
        val signalled = signals.signals.size

        val refused = assertFailsWith<OpenFspException> { settler.attest(payment.id, Attestation("ops@merchant.example", PaymentStatus.FAILED, "a call")) }
        assertEquals(ProblemType.STATE_CONFLICT, refused.type)
        assertEquals(1, journal.attestations.size)
        assertEquals(signalled, signals.signals.size)
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

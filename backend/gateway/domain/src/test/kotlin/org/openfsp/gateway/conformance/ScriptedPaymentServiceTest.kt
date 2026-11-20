package org.openfsp.gateway.conformance

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.conformance.spi.CallbackDelivery
import org.openfsp.gateway.conformance.spi.stubs.ManualDeferrals
import org.openfsp.gateway.payment.CallbackTokens
import org.openfsp.gateway.payment.CreatePaymentCommand
import org.openfsp.gateway.payment.CreationRequest
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentCreator
import org.openfsp.gateway.payment.PaymentSettler
import org.openfsp.gateway.payment.PaymentSynchronizer
import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.spi.CallbackUrls
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingSynchronizationLimits
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.Effect
import org.openfsp.protocol.FailureReason
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

class ScriptedPaymentServiceTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val lookupOnly = ProviderStub()
    private val notifying =
        ProviderStub(
            id = "mock_notifying",
            descriptor =
                ProviderDescriptor(
                    displayName = "Notifying",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = false,
                    signedCallbacks = true,
                    paymentUrl = true,
                ),
        )
    private val signedOnly =
        ProviderStub(
            id = "mock_signed",
            descriptor =
                ProviderDescriptor(
                    displayName = "Signed",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.FEE),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = false,
                    signedCallbacks = true,
                ),
        )
    private val blind =
        ProviderStub(
            id = "mock_blind",
            descriptor =
                ProviderDescriptor(
                    displayName = "Blind",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.REDIRECT),
                    payerRequired = false,
                    idempotentCreation = false,
                ),
        )
    private val stubs = listOf(lookupOnly, notifying, signedOnly, blind)
    private val payments = InMemoryPayments()
    private val journal = InMemoryPaymentJournal()
    private val published = mutableListOf<PaymentTransitioned>()
    private val events = RecordPaymentEvents { published += it }
    private val signals = RecordingOperatorSignals()
    private val transactions = ImmediateTransactions()
    private val tokens = CallbackTokens(ByteArray(32) { 9 })
    private val callbackUrls = CallbackUrls { provider, token -> URI("https://gateway.example/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
    private val deferrals = ManualDeferrals()
    private val settings = ConformanceSettings(Duration.ofHours(1), 2, URI("https://gateway.example/conformance/control"), Duration.ofMillis(500), Duration.ofMinutes(2))
    private val settlement = mutableListOf<PaymentSettler>()
    private val delivery =
        CallbackDelivery { provider, token, request ->
            if (token == null) settlement.single().onSignedCallback(provider, request) else settlement.single().onPaymentUrlCallback(provider, token, request)
        }
    private val scripts = PaymentScripts(payments, calendar, deferrals, delivery, settings)
    private val providers: Providers = ScriptedProviders(InMemoryProviders(stubs), scripts)
    private val settler = PaymentSettler(payments, journal, events, providers, transactions, signals, tokens, calendar, callbackUrls).also(settlement::add)
    private val creator = PaymentCreator(providers, payments, journal, events, settler, transactions, signals, OrderIdDerivation(ByteArray(32) { 7 }), tokens, calendar, callbackUrls)
    private val control = ConformanceControl(scripts, payments, providers, settler)
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar, RecordingSynchronizationLimits())

    init {
        scripts.open(ConformanceRun(RUN_ACCOUNT, RUN, clock.instant().plus(settings.lifetime)))
        scripts.open(ConformanceRun(OTHER_ACCOUNT, OTHER_RUN, clock.instant().plus(settings.lifetime)))
    }

    private fun command(
        reference: String,
        provider: String = lookupOnly.id,
        principal: String = RUN,
        account: String = RUN_ACCOUNT,
    ) = CreatePaymentCommand(account, reference, Money.htg(125_000), provider, request = CreationRequest(principal, "key-$reference", "fingerprint-$reference"))

    private fun scripted(
        scenario: Scenario,
        provider: String = lookupOnly.id,
        reference: String = "INV-${scenario.name}",
    ): Payment {
        control.declare(RUN_ACCOUNT, RUN, reference, scenario.name)
        return creator.create(command(reference, provider))
    }

    private fun settle() = deferrals.runDue(settings.settleAfter)

    private fun externalCalls() = stubs.sumOf { it.requests.size }

    private fun stored(payment: Payment) = payments.find(payment.id)!!

    private fun sources(payment: Payment) = journal.transitions(payment.id).map { it.source }

    @Test
    fun `a payment without a declared scenario reaches the real payment service`() {
        control.declare(RUN_ACCOUNT, RUN, "INV-DECLARED", Scenario.DECLINE.name)
        val plain = creator.create(command("INV-PLAIN"))
        val foreign = creator.create(command("INV-DECLARED", principal = "prn_outsider", account = "acct_outsider"))
        assertEquals(2, lookupOnly.requests.size)
        assertEquals("STUB-${plain.providerOrderId}", plain.providerReference)
        assertEquals("STUB-${foreign.providerOrderId}", foreign.providerReference)
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, plain.id).status)
    }

    @Test
    fun `SUCCESS is played without the real service and settles through the notification of the provider`() {
        val payment = scripted(Scenario.SUCCESS, notifying.id)
        assertEquals(PaymentStatus.PENDING, payment.status)
        assertEquals(NextAction.payerApproval, payment.nextAction)
        assertTrue(payment.providerReference!!.startsWith("SCRIPTED-"))
        assertEquals(ControlledPayment(payment.id, settled = false, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))

        settle()

        assertEquals(PaymentStatus.SUCCEEDED, stored(payment).status)
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.SIGNED_CALLBACK), sources(payment))
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 1), control.state(RUN_ACCOUNT, RUN, payment.reference))
        assertEquals(listOf(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING), PaymentTransitioned(payment.id, PaymentStatus.PENDING, PaymentStatus.SUCCEEDED)), published)
        assertEquals(0, externalCalls())
    }

    @Test
    fun `a key of the second principal of the run follows the declared scenario`() {
        control.declare(RUN_ACCOUNT, RUN, "INV-SECOND", Scenario.SUCCESS.name)
        val payment = creator.create(command("INV-SECOND", principal = SECOND))
        assertTrue(payment.providerReference!!.startsWith("SCRIPTED-"))
        assertEquals(0, externalCalls())
    }

    @Test
    fun `DECLINE and INSUFFICIENT fail with their reason, observed by lookup where the provider sends no notification`() {
        val declined = scripted(Scenario.DECLINE)
        val insufficient = scripted(Scenario.INSUFFICIENT)
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, declined.id).status)

        settle()

        assertEquals(PaymentStatus.PENDING, stored(declined).status)
        assertEquals(ControlledPayment(declined.id, settled = true, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, declined.reference))
        val failed = synchronizer.current(RUN_ACCOUNT, declined.id)
        assertEquals(PaymentStatus.FAILED to FailureReason.DECLINED, failed.status to failed.failureReason)
        assertEquals(mapOf("code" to "DECLINED", "message" to "The payment was declined"), failed.failureDetail)
        assertEquals(FailureReason.INSUFFICIENT_FUNDS, synchronizer.current(RUN_ACCOUNT, insufficient.id).failureReason)
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.LOOKUP), sources(declined))
        assertEquals(0, externalCalls())
    }

    @Test
    fun `TIMEOUT accepts the payment, answers provider-timeout and is found again on the resend`() {
        control.declare(RUN_ACCOUNT, RUN, "INV-TIMEOUT", Scenario.TIMEOUT.name)
        val failure = assertFailsWith<OpenFspException> { creator.create(command("INV-TIMEOUT")) }
        assertEquals(ProblemType.PROVIDER_TIMEOUT, failure.type)
        val pending = payments.withReference(RUN_ACCOUNT, "INV-TIMEOUT")!!
        assertTrue(pending.creationOutcomeUnknown)

        val resent = creator.create(command("INV-TIMEOUT"))
        assertEquals(pending.id, resent.id)
        assertEquals(NextAction.payerApproval, resent.nextAction)
        settle()
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(RUN_ACCOUNT, resent.id).status)
        assertEquals(0, externalCalls())
    }

    @Test
    fun `PENDING_FOREVER stays pending until the control interface resolves it`() {
        val payment = scripted(Scenario.PENDING_FOREVER, notifying.id)
        settle()
        clock.advance(Duration.ofMinutes(10))
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, payment.id).status)

        control.resolve(RUN_ACCOUNT, RUN, payment.id, "failed", "payer_canceled")
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))
        deferrals.runDue()

        val failed = stored(payment)
        assertEquals(PaymentStatus.FAILED to FailureReason.PAYER_CANCELED, failed.status to failed.failureReason)
        assertEquals(1, control.state(RUN_ACCOUNT, RUN, payment.reference).callbacksSent)
        control.resolve(RUN_ACCOUNT, RUN, payment.id, "succeeded", null)
        deferrals.runDue()
        assertEquals(PaymentStatus.FAILED, stored(payment).status)
        assertEquals(1, control.state(RUN_ACCOUNT, RUN, payment.reference).callbacksSent)
    }

    @Test
    fun `the control interface refuses an unknown outcome or reason`() {
        val payment = scripted(Scenario.PENDING_FOREVER)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { control.resolve(RUN_ACCOUNT, RUN, payment.id, "canceled", null) }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { control.resolve(RUN_ACCOUNT, RUN, payment.id, "failed", "bad_mood") }.type)
        control.resolve(RUN_ACCOUNT, RUN, payment.id, "failed", null)
        assertEquals(FailureReason.UNSPECIFIED, synchronizer.current(RUN_ACCOUNT, payment.id).failureReason)
    }

    @Test
    fun `EXPIRE gives the payment one minute and expires it at the provider`() {
        val payment = scripted(Scenario.EXPIRE, notifying.id)
        assertEquals(clock.instant().plus(PaymentScripts.EXPIRY_WINDOW), payment.expiresAt)
        settle()
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, payment.id).status)

        clock.advance(PaymentScripts.EXPIRY_WINDOW)
        assertEquals(PaymentStatus.EXPIRED, synchronizer.current(RUN_ACCOUNT, payment.id).status)
        assertNull(stored(payment).failureReason)
        deferrals.runDue(PaymentScripts.EXPIRY_WINDOW)
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 1), control.state(RUN_ACCOUNT, RUN, payment.reference))
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.LOOKUP), sources(payment))
    }

    @Test
    fun `EXPIRE notifies the expiry by itself where the provider has no lookup`() {
        val payment = scripted(Scenario.EXPIRE, signedOnly.id)
        clock.advance(PaymentScripts.EXPIRY_WINDOW)
        deferrals.runDue(PaymentScripts.EXPIRY_WINDOW)
        assertEquals(PaymentStatus.EXPIRED, stored(payment).status)
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.SIGNED_CALLBACK), sources(payment))
    }

    @Test
    fun `NO_CALLBACK reaches its outcome without any notification`() {
        val payment = scripted(Scenario.NO_CALLBACK, notifying.id)
        settle()
        deferrals.runDue(settings.lateCallbackAfter)
        assertEquals(PaymentStatus.PENDING, stored(payment).status)
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(RUN_ACCOUNT, payment.id).status)

        control.callback(RUN_ACCOUNT, RUN, payment.id)
        assertEquals(1, control.state(RUN_ACCOUNT, RUN, payment.reference).callbacksSent)
    }

    @Test
    fun `LATE_CALLBACK holds the notification for the late delay`() {
        val payment = scripted(Scenario.LATE_CALLBACK, signedOnly.id)
        settle()
        assertEquals(PaymentStatus.PENDING, stored(payment).status)
        assertEquals(listOf(settings.lateCallbackAfter), deferrals.delays)

        deferrals.runDue(settings.lateCallbackAfter)
        assertEquals(PaymentStatus.SUCCEEDED, stored(payment).status)
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 1), control.state(RUN_ACCOUNT, RUN, payment.reference))
    }

    @Test
    fun `DUPLICATE_CALLBACK notifies three times and the payment transitions once`() {
        val payment = scripted(Scenario.DUPLICATE_CALLBACK, signedOnly.id)
        settle()
        assertEquals(PaymentStatus.SUCCEEDED, stored(payment).status)
        assertEquals(3, control.state(RUN_ACCOUNT, RUN, payment.reference).callbacksSent)
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.SIGNED_CALLBACK), sources(payment))
        assertEquals(2, published.size)
    }

    @Test
    fun `UNKNOWN_STATUS leaves the payment pending after a lookup`() {
        val payment = scripted(Scenario.UNKNOWN_STATUS, notifying.id)
        settle()
        assertTrue(control.state(RUN_ACCOUNT, RUN, payment.reference).settled)
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, payment.id).status)
        assertEquals(0, control.state(RUN_ACCOUNT, RUN, payment.reference).callbacksSent)
    }

    @Test
    fun `PROVIDER_ERROR answers provider-error with an unknown effect, again on the resend`() {
        control.declare(RUN_ACCOUNT, RUN, "INV-ERROR", Scenario.PROVIDER_ERROR.name)
        repeat(2) {
            val failure = assertFailsWith<OpenFspException> { creator.create(command("INV-ERROR")) }
            assertEquals(ProblemType.PROVIDER_ERROR to Effect.UNKNOWN, failure.type to failure.effect)
        }
        val pending = payments.withReference(RUN_ACCOUNT, "INV-ERROR")!!
        assertEquals(ControlledPayment(pending.id, settled = false, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, "INV-ERROR"))
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, pending.id).status)
        assertEquals(ProblemType.STATE_CONFLICT, assertFailsWith<OpenFspException> { control.resolve(RUN_ACCOUNT, RUN, pending.id, "succeeded", null) }.type)
        assertEquals(0, externalCalls())
    }

    @Test
    fun `a notification scenario on a provider without notifications reaches its outcome silently`() {
        val payment = scripted(Scenario.DUPLICATE_CALLBACK)
        settle()
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(RUN_ACCOUNT, payment.id).status)
    }

    @Test
    fun `an outcome at a provider without lookup nor notification is never observed`() {
        val payment = scripted(Scenario.SUCCESS, blind.id)
        assertEquals(NextAction.Type.REDIRECT, payment.nextAction!!.type)
        assertEquals("https://gateway.example/conformance/control/checkout/${payment.providerReference}", payment.nextAction.redirectUrl)
        settle()
        assertTrue(control.state(RUN_ACCOUNT, RUN, payment.reference).settled)
        assertEquals(PaymentStatus.PENDING, stored(payment).status)
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, payment.id).status)
    }

    @Test
    fun `resolve records the outcome on the payment itself where the provider has no lookup nor notification`() {
        val payment = scripted(Scenario.PENDING_FOREVER, blind.id)
        control.resolve(RUN_ACCOUNT, RUN, payment.id, "failed", "declined")
        deferrals.runDue()

        val failed = stored(payment)
        assertEquals(PaymentStatus.FAILED to FailureReason.DECLINED, failed.status to failed.failureReason)
        assertEquals(listOf(TransitionSource.CREATION, TransitionSource.ATTESTATION), sources(payment))
        assertEquals(PaymentTransitioned(payment.id, PaymentStatus.PENDING, PaymentStatus.FAILED), published.last())
        assertEquals(ControlledPayment(payment.id, settled = true, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))
        control.resolve(RUN_ACCOUNT, RUN, payment.id, "succeeded", null)
        assertEquals(PaymentStatus.FAILED, stored(payment).status)

        val settledUnseen = scripted(Scenario.SUCCESS, blind.id)
        settle()
        assertEquals(PaymentStatus.PENDING, stored(settledUnseen).status)
        control.resolve(RUN_ACCOUNT, RUN, settledUnseen.id, "expired", null)
        assertEquals(PaymentStatus.SUCCEEDED, stored(settledUnseen).status)
    }

    @Test
    fun `a provider announcing payments_fee reports a fee with the success`() {
        val payment = scripted(Scenario.SUCCESS, signedOnly.id)
        settle()
        assertEquals(ProviderFee(Money.htg(2_500), FeeBearer.MERCHANT), stored(payment).fee)
        val failed = scripted(Scenario.DECLINE, signedOnly.id)
        settle()
        assertNull(stored(failed).fee)
    }

    @Test
    fun `a declaration is refused for an unknown scenario or one no provider can play`() {
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { control.declare(RUN_ACCOUNT, RUN, "INV-1", "CONTRADICT") }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { control.declare(RUN_ACCOUNT, RUN, "INV-1", null) }.type)

        val silent = ConformanceControl(scripts, payments, InMemoryProviders(listOf(blind)), settler)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { silent.declare(RUN_ACCOUNT, RUN, "INV-1", Scenario.LATE_CALLBACK.name) }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { silent.declare(RUN_ACCOUNT, RUN, "INV-1", Scenario.UNKNOWN_STATUS.name) }.type)
        silent.declare(RUN_ACCOUNT, RUN, "INV-1", Scenario.SUCCESS.name)
    }

    @Test
    fun `a run neither sees nor acts on the payments of another run`() {
        val payment = scripted(Scenario.PENDING_FOREVER)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { control.state(OTHER_ACCOUNT, OTHER_RUN, payment.reference) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { control.resolve(OTHER_ACCOUNT, OTHER_RUN, payment.id, "succeeded", null) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { control.callback(OTHER_ACCOUNT, OTHER_RUN, payment.id) }.type)
        assertEquals(ProblemType.FORBIDDEN, assertFailsWith<OpenFspException> { control.state(RUN_ACCOUNT, SECOND, payment.reference) }.type)
        assertEquals(ProblemType.FORBIDDEN, assertFailsWith<OpenFspException> { control.state("acct_outsider", "prn_outsider", payment.reference) }.type)

        control.declare(OTHER_ACCOUNT, OTHER_RUN, "INV-SHARED", Scenario.DECLINE.name)
        val mine = creator.create(command("INV-SHARED"))
        assertEquals("STUB-${mine.providerOrderId}", mine.providerReference)
        assertEquals(ControlledPayment(payment.id, settled = false, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, payment.reference))
    }

    @Test
    fun `a payment of the run without scenario is reported from its own state and cannot be resolved`() {
        val payment = creator.create(command("INV-PLAIN"))
        assertEquals(ControlledPayment(payment.id, settled = false, callbacksSent = 0), control.state(RUN_ACCOUNT, RUN, "INV-PLAIN"))
        assertEquals(ProblemType.STATE_CONFLICT, assertFailsWith<OpenFspException> { control.resolve(RUN_ACCOUNT, RUN, payment.id, "succeeded", null) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { control.state(RUN_ACCOUNT, RUN, "INV-ABSENT") }.type)
    }

    @Test
    fun `the interface stops answering at the expiry of the run and its scripts are swept`() {
        val payment = scripted(Scenario.PENDING_FOREVER)
        clock.advance(settings.lifetime)
        assertEquals(ProblemType.FORBIDDEN, assertFailsWith<OpenFspException> { control.state(RUN_ACCOUNT, RUN, payment.reference) }.type)
        assertEquals(ProblemType.FORBIDDEN, assertFailsWith<OpenFspException> { control.declare(RUN_ACCOUNT, RUN, "INV-LATE", Scenario.SUCCESS.name) }.type)

        scripts.sweep()
        assertEquals(PaymentStatus.PENDING, synchronizer.current(RUN_ACCOUNT, payment.id).status)
        assertEquals(0, lookupOnly.requests.size)
    }
}

private const val RUN = "prn_run"
private const val SECOND = "prn_run_second"
private const val OTHER_RUN = "prn_other"
private const val RUN_ACCOUNT = "acct_run"
private const val OTHER_ACCOUNT = "acct_other"

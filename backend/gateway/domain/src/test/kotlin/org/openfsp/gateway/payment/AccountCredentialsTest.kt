package org.openfsp.gateway.payment

import org.junit.jupiter.api.Test
import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.capability.CapabilityCatalog
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.payment.api.CallbackOutcome
import org.openfsp.gateway.payment.spi.CallbackUrls
import org.openfsp.gateway.payment.spi.stubs.InMemoryPaymentJournal
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.payment.spi.stubs.RecordingPaymentEvents
import org.openfsp.gateway.payment.spi.stubs.RecordingSynchronizationLimits
import org.openfsp.gateway.provider.PaymentCredentials
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.gateway.webhook.spi.stubs.FixedSubscriptions
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountCredentialsTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val descriptor =
        ProviderDescriptor(
            displayName = "Notifying",
            capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
            nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
            payerRequired = false,
            lookupNotifications = true,
        )
    private val platform = ProviderStub(id = "mock_wallet", descriptor = descriptor)
    private val own = ProviderStub(id = "mock_wallet", descriptor = descriptor)
    private val onlyOwn = ProviderStub(id = "mock_private", descriptor = descriptor)
    private val providers = InMemoryProviders(listOf(platform))
    private val ownRevision = providers.addForAccount(SHOP, own)
    private val onlyOwnRevision = providers.addForAccount(SHOP, onlyOwn)
    private val payments = InMemoryPayments()
    private val journal = InMemoryPaymentJournal()
    private val signals = RecordingOperatorSignals()
    private val tokens = CallbackTokens(ByteArray(32) { 9 })
    private val urls = CallbackUrls { provider, token -> URI("https://gateway.example/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
    private val settler = PaymentSettler(payments, journal, RecordingPaymentEvents(), providers, ImmediateTransactions(), signals, tokens, calendar, urls)
    private val creator = PaymentCreator(providers, payments, journal, RecordingPaymentEvents(), settler, ImmediateTransactions(), signals, OrderIdDerivation(ByteArray(32) { 7 }), tokens, calendar, urls)
    private val synchronizer = PaymentSynchronizer(payments, providers, settler, signals, calendar, RecordingSynchronizationLimits())

    private fun create(
        account: String,
        provider: String = "mock_wallet",
        reference: String = "INV-1",
    ) = creator.create(CreatePaymentCommand(account, reference, Money.htg(125_000), provider))

    @Test
    fun `an account with its own credentials creates and looks up with them, others with the platform credentials`() {
        val ofShop = create(SHOP)
        val ofOther = create(OTHER)
        assertEquals(PaymentCredentials.Account(ownRevision), ofShop.credentials)
        assertEquals(PaymentCredentials.Platform, ofOther.credentials)
        assertEquals(1, own.requests.size)
        assertEquals(1, platform.requests.size)

        own.statuses[ofShop.providerOrderId] = PaymentStatus.SUCCEEDED
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(SHOP, ofShop.id).status)
        assertEquals(PaymentStatus.PENDING, synchronizer.current(OTHER, ofOther.id).status)
    }

    @Test
    fun `a payment keeps the credentials of its creation even when the account loses them`() {
        val payment = create(SHOP)
        val withoutOwn = InMemoryProviders(listOf(platform))
        val reader = PaymentSynchronizer(payments, withoutOwn, settler, signals, calendar, RecordingSynchronizationLimits())
        platform.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED
        assertEquals(PaymentStatus.PENDING, reader.current(SHOP, payment.id).status)
        assertTrue(platform.requests.isEmpty())
    }

    @Test
    fun `a service without platform credentials is served only to the accounts that hold their own`() {
        assertEquals(PaymentCredentials.Account(onlyOwnRevision), create(SHOP, "mock_private").credentials)
        val refused = assertFailsWith<OpenFspException> { create(OTHER, "mock_private") }
        assertEquals("/provider", refused.errors.single().field)

        val catalog = CapabilityCatalog(providers, FixedSubscriptions())
        assertEquals(listOf("mock_private", "mock_wallet"), catalog.describe(AccountFilter.Only(SHOP)).map { it.id })
        assertEquals(listOf("mock_wallet"), catalog.describe(AccountFilter.Only(OTHER)).map { it.id })
    }

    @Test
    fun `a notification acts only on the payments created with the credentials of its address`() {
        val ofShop = create(SHOP)
        val ofOther = create(OTHER, reference = "INV-2")
        own.statuses[ofShop.providerOrderId] = PaymentStatus.SUCCEEDED
        platform.statuses[ofOther.providerOrderId] = PaymentStatus.SUCCEEDED

        assertIs<CallbackOutcome.UnknownPayment>(synchronizer.onLookupNotification("mock_wallet", null, mapOf("order_id" to ofShop.providerOrderId)))
        assertIs<CallbackOutcome.UnknownProvider>(synchronizer.onLookupNotification("mock_wallet", OTHER, mapOf("order_id" to ofOther.providerOrderId)))
        assertIs<CallbackOutcome.UnknownProvider>(synchronizer.onLookupNotification("mock_wallet", OTHER, mapOf("order_id" to ofShop.providerOrderId)))
        assertEquals(PaymentStatus.PENDING, payments.find(ofShop.id)!!.status)

        assertIs<CallbackOutcome.Applied>(synchronizer.onLookupNotification("mock_wallet", SHOP, mapOf("order_id" to ofShop.providerOrderId)))
        assertIs<CallbackOutcome.Applied>(synchronizer.onLookupNotification("mock_wallet", null, mapOf("order_id" to ofOther.providerOrderId)))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(ofShop.id)!!.status)
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(ofOther.id)!!.status)
        assertNull(synchronizer.returnTarget("mock_wallet", OTHER, mapOf("order_id" to ofShop.providerOrderId)))
    }

    @Test
    fun `after a rotation a payment is still looked up with the revision that created it, new payments with the active one`() {
        val before = create(SHOP)
        val rotated = ProviderStub(id = "mock_wallet", descriptor = descriptor)
        val rotation = providers.addForAccount(SHOP, rotated)
        val after = create(SHOP, reference = "INV-2")

        assertEquals(PaymentCredentials.Account(rotation), after.credentials)
        assertEquals(1, rotated.requests.size)
        own.statuses[before.providerOrderId] = PaymentStatus.SUCCEEDED
        rotated.statuses[before.providerOrderId] = PaymentStatus.FAILED
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(SHOP, before.id).status)

        own.statuses[after.providerOrderId] = PaymentStatus.FAILED
        rotated.statuses[after.providerOrderId] = PaymentStatus.SUCCEEDED
        assertIs<CallbackOutcome.Applied>(synchronizer.onLookupNotification("mock_wallet", SHOP, mapOf("order_id" to after.providerOrderId)))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(after.id)!!.status)
    }

    @Test
    fun `a retired revision still serves the payments it created`() {
        val payment = create(SHOP)
        providers.retire(ownRevision)
        own.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED
        clock.advance(ReconciliationTurn.FIRST_DELAY.plusSeconds(1))

        assertEquals(1, synchronizer.reconcile(10))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
    }

    @Test
    fun `no provider call is made for a payment on a revoked revision, which stays pending for the operator`() {
        val payment = create(SHOP)
        providers.revoke(ownRevision)
        own.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED
        clock.advance(ReconciliationTurn.FIRST_DELAY.plusSeconds(1))

        assertEquals(PaymentStatus.PENDING, synchronizer.current(SHOP, payment.id).status)
        assertEquals(0, synchronizer.reconcile(10))
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
        assertTrue(signals.signals.any { (subject, message) -> subject == payment.id && "no longer usable" in message })
        assertIs<CallbackOutcome.UnknownProvider>(synchronizer.onLookupNotification("mock_wallet", SHOP, mapOf("order_id" to payment.providerOrderId)))
        assertNull(synchronizer.returnTarget("mock_wallet", SHOP, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
    }

    @Test
    fun `a notification for a payment on a revoked revision is refused even while the account holds another revision`() {
        val payment = create(SHOP)
        val rotated = ProviderStub(id = "mock_wallet", descriptor = descriptor)
        providers.addForAccount(SHOP, rotated)
        providers.revoke(ownRevision)
        own.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED
        rotated.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED

        assertIs<CallbackOutcome.Unverified>(synchronizer.onLookupNotification("mock_wallet", SHOP, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
    }

    @Test
    fun `after its credentials are removed, a notification for a pending payment is verified with the retired revision that created it`() {
        val payment = create(SHOP)
        providers.retire(ownRevision)
        own.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED

        assertIs<CallbackOutcome.UnknownProvider>(synchronizer.onLookupNotification("mock_wallet", OTHER, mapOf("order_id" to payment.providerOrderId)))
        assertIs<CallbackOutcome.UnknownPayment>(synchronizer.onLookupNotification("mock_wallet", null, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.PENDING, payments.find(payment.id)!!.status)
        assertIs<CallbackOutcome.Applied>(synchronizer.onLookupNotification("mock_wallet", SHOP, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
    }

    @Test
    fun `a payment created with the platform credentials is verified with them after the account switches to its own`() {
        val payment = create(OTHER)
        providers.addForAccount(OTHER, ProviderStub(id = "mock_wallet", descriptor = descriptor))
        platform.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED

        assertIs<CallbackOutcome.UnknownPayment>(synchronizer.onLookupNotification("mock_wallet", OTHER, mapOf("order_id" to payment.providerOrderId)))
        assertIs<CallbackOutcome.Applied>(synchronizer.onLookupNotification("mock_wallet", null, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
    }

    @Test
    fun `the payer coming back after a removal is redirected and the payment looked up with its retired revision`() {
        val payment = creator.create(CreatePaymentCommand(SHOP, "INV-9", Money.htg(125_000), "mock_wallet", returnUrl = URI("https://shop.example/back")))
        providers.retire(ownRevision)
        own.statuses[payment.providerOrderId] = PaymentStatus.SUCCEEDED

        assertEquals(URI("https://shop.example/back"), synchronizer.returnTarget("mock_wallet", SHOP, mapOf("order_id" to payment.providerOrderId)))
        assertEquals(PaymentStatus.SUCCEEDED, payments.find(payment.id)!!.status)
        assertNull(synchronizer.returnTarget("mock_wallet", OTHER, mapOf("order_id" to payment.providerOrderId)))
    }

    @Test
    fun `an account using its own credentials without an active revision is refused, never served with the platform credentials`() {
        providers.revoke(ownRevision)
        providers.revoke(onlyOwnRevision)

        val refused = assertFailsWith<OpenFspException> { create(SHOP) }
        assertEquals("/provider", refused.errors.single().field)
        assertTrue("no active credentials" in refused.errors.single().detail.orEmpty())
        assertFailsWith<OpenFspException> { create(SHOP, "mock_private", reference = "INV-2") }
        assertTrue(own.requests.isEmpty() && onlyOwn.requests.isEmpty() && platform.requests.isEmpty())
        assertEquals(listOf("mock_wallet"), CapabilityCatalog(providers, FixedSubscriptions()).describe(AccountFilter.Only(OTHER)).map { it.id })
        assertTrue(CapabilityCatalog(providers, FixedSubscriptions()).describe(AccountFilter.Only(SHOP)).isEmpty())
    }

    @Test
    fun `an account switched to the platform credentials creates with them while its pending payment keeps its revision, other accounts untouched`() {
        val otherOwn = ProviderStub(id = "mock_wallet", descriptor = descriptor)
        val otherRevision = providers.addForAccount(OTHER, otherOwn)
        val pending = create(SHOP)
        providers.usePlatform(SHOP, "mock_wallet")

        assertEquals(PaymentCredentials.Platform, create(SHOP, reference = "INV-2").credentials)
        assertEquals(PaymentCredentials.Account(otherRevision), create(OTHER, reference = "INV-3").credentials)
        own.statuses[pending.providerOrderId] = PaymentStatus.SUCCEEDED
        platform.statuses[pending.providerOrderId] = PaymentStatus.FAILED
        assertEquals(PaymentStatus.SUCCEEDED, synchronizer.current(SHOP, pending.id).status)
    }

    @Test
    fun `the platform mode without platform credentials does not serve the provider`() {
        providers.usePlatform(SHOP, "mock_private")

        val refused = assertFailsWith<OpenFspException> { create(SHOP, "mock_private") }
        assertTrue("not served" in refused.errors.single().detail.orEmpty())
        assertTrue(onlyOwn.requests.isEmpty())
    }

    private companion object {
        const val SHOP = "acct_shop"
        const val OTHER = "acct_other"
    }
}

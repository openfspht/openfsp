package org.openfsp.gateway.provider

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.AccountRegistry
import org.openfsp.gateway.account.spi.stubs.InMemoryMerchantAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.provider.spi.CredentialedProviders
import org.openfsp.gateway.provider.spi.stubs.InMemoryAccountCredentialStore
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.ProblemType
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderCredentialsManagerTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val audit = InMemoryAuditTrail()
    private val accounts = AccountRegistry(InMemoryMerchantAccounts(), audit, calendar)
    private val store = InMemoryAccountCredentialStore(calendar)
    private val monCash = CredentialedProvider("moncash", "MonCash", listOf("client-id", "client-secret"), hasPlatformCredentials = true, lookupNotifications = false)
    private val natCash = CredentialedProvider("natcash", "NatCash", listOf("username"), hasPlatformCredentials = false, lookupNotifications = true)
    private val openFsp = CredentialedProvider("openfsp", "OpenFSP", emptyList(), hasPlatformCredentials = true, lookupNotifications = false)
    private val catalog =
        object : CredentialedProviders {
            override fun all() = listOf(monCash, natCash, openFsp)

            override fun find(id: String) = all().firstOrNull { it.id == id }
        }
    private val manager = ProviderCredentialsManager(accounts, catalog, store, audit, calendar)

    @Test
    fun `each provider reports where the credentials of the account come from`() {
        val shop = accounts.open("shop").id
        manager.save(shop, "natcash", mapOf("username" to " merchant "))

        val listed = manager.providersOf(shop).associateBy { it.provider.id }
        assertEquals(CredentialOrigin.PLATFORM, listed.getValue("moncash").origin)
        assertEquals(CredentialOrigin.ACCOUNT, listed.getValue("natcash").origin)
        assertEquals(clock.instant(), listed.getValue("natcash").updatedAt)
        assertTrue(listed.getValue("natcash").notifiesAccount)
        assertEquals(CredentialOrigin.PLATFORM, listed.getValue("openfsp").origin)
        assertEquals(mapOf("username" to "merchant"), store.saved[shop to "natcash"])
        assertEquals(AuditKind.PROVIDER_CREDENTIALS_SET, audit.records.last().kind)
    }

    @Test
    fun `the credentials of one account are invisible to another`() {
        val shop = accounts.open("shop").id
        val other = accounts.open("other").id
        manager.save(shop, "natcash", mapOf("username" to "merchant"))

        val listed = manager.providersOf(other).single { it.provider.id == "natcash" }
        assertEquals(CredentialOrigin.NONE, listed.origin)
        assertNull(listed.updatedAt)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.remove(other, "natcash") }.type)
    }

    @Test
    fun `credentials are refused when absent, unknown, empty or too long`() {
        val shop = accounts.open("shop").id
        assertEquals(ProblemType.MISSING_FIELD, assertFailsWith<OpenFspException> { manager.save(shop, "moncash", null) }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { manager.save(shop, "moncash", mapOf("client-id" to "a", "client-secret" to "b", "pin" to "c")) }.type)
        val empty = assertFailsWith<OpenFspException> { manager.save(shop, "moncash", mapOf("client-id" to "a", "client-secret" to " ")) }
        assertEquals("/credentials/client-secret" to FieldErrorCode.REQUIRED, empty.errors.single().let { it.field to it.code })
        val long = assertFailsWith<OpenFspException> { manager.save(shop, "moncash", mapOf("client-id" to "a".repeat(8193), "client-secret" to "b")) }
        assertEquals("/credentials/client-id" to FieldErrorCode.TOO_LONG, long.errors.single().let { it.field to it.code })
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `an unknown account, an expired account or a provider without account credentials is refused`() {
        val expiring = accounts.open("expiring", clock.instant().plusSeconds(60)).id
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.providersOf("acct_unknown") }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.save("acct_unknown", "natcash", mapOf("username" to "a")) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.save(expiring, "openfsp", emptyMap()) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.save(expiring, "absent", emptyMap()) }.type)
        clock.advance(Duration.ofMinutes(2))
        assertEquals(ProblemType.STATE_CONFLICT, assertFailsWith<OpenFspException> { manager.save(expiring, "natcash", mapOf("username" to "a")) }.type)
    }

    @Test
    fun `removed credentials are audited and a second removal is not found`() {
        val shop = accounts.open("shop").id
        manager.save(shop, "natcash", mapOf("username" to "merchant"))
        manager.remove(shop, "natcash")
        assertEquals(AuditKind.PROVIDER_CREDENTIALS_REMOVED, audit.records.last().kind)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.remove(shop, "natcash") }.type)
    }
}

package org.openfsp.gateway.provider

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.account.AccountRegistry
import org.openfsp.gateway.account.spi.stubs.InMemoryMerchantAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
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
    private val store = InMemoryAccountCredentialStore()
    private val monCash = CredentialedProvider("moncash", "MonCash", listOf("client-id", "client-secret"), hasPlatformCredentials = true, lookupNotifications = false)
    private val natCash = CredentialedProvider("natcash", "NatCash", listOf("username"), hasPlatformCredentials = false, lookupNotifications = true)
    private val openFsp = CredentialedProvider("openfsp", "OpenFSP", emptyList(), hasPlatformCredentials = true, lookupNotifications = false)
    private val catalog =
        object : CredentialedProviders {
            override fun all() = listOf(monCash, natCash, openFsp)

            override fun find(id: String) = all().firstOrNull { it.id == id }
        }
    private val manager = ProviderCredentialsManager(accounts, catalog, store, audit, calendar, ImmediateTransactions())

    @Test
    fun `each provider reports where the credentials of the account come from`() {
        val shop = accounts.open("shop").id
        manager.save(shop, "natcash", mapOf("username" to " merchant "))

        val listed = manager.providersOf(shop).associateBy { it.provider.id }
        assertEquals(CredentialOrigin.PLATFORM, listed.getValue("moncash").origin)
        assertEquals(CredentialMode.PLATFORM, listed.getValue("moncash").mode)
        assertEquals(CredentialOrigin.ACCOUNT, listed.getValue("natcash").origin)
        assertEquals(CredentialMode.OWN, listed.getValue("natcash").mode)
        assertEquals(clock.instant(), listed.getValue("natcash").updatedAt)
        assertTrue(listed.getValue("natcash").notifiesAccount)
        assertEquals(CredentialOrigin.PLATFORM, listed.getValue("openfsp").origin)
        assertEquals(mapOf("username" to "merchant"), store.secrets[store.activeOf(shop, "natcash")!!.id])
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
        assertTrue(store.revisions.isEmpty())
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

    @Test
    fun `a rotation creates an active revision and retires the previous one, which stays sealed`() {
        val shop = accounts.open("shop").id
        val first = manager.save(shop, "natcash", mapOf("username" to "first"))
        clock.advance(Duration.ofMinutes(1))
        val second = manager.save(shop, "natcash", mapOf("username" to "second"))

        val revisions = manager.providersOf(shop).single { it.provider.id == "natcash" }.revisions
        assertEquals(listOf(second.id to CredentialStatus.ACTIVE, first.id to CredentialStatus.RETIRED), revisions.map { it.id to it.status })
        assertEquals(clock.instant(), revisions.last().retiredAt)
        assertTrue(revisions.all { it.sealed })
        assertEquals(mapOf("username" to "first"), store.secrets[first.id])
        assertEquals("natcash ${second.id}", audit.records.last().detail)
    }

    @Test
    fun `removing the credentials retires the active revision`() {
        val shop = accounts.open("shop").id
        val saved = manager.save(shop, "natcash", mapOf("username" to "merchant"))
        manager.remove(shop, "natcash")

        val listed = manager.providersOf(shop).single { it.provider.id == "natcash" }
        assertEquals(CredentialOrigin.NONE, listed.origin)
        assertEquals(CredentialStatus.RETIRED, listed.revisions.single().status)
        assertEquals(mapOf("username" to "merchant"), store.secrets[saved.id])
    }

    @Test
    fun `a revocation wipes the revision at once, is audited and leaves the account without active credentials`() {
        val shop = accounts.open("shop").id
        val retired = manager.save(shop, "natcash", mapOf("username" to "old"))
        val active = manager.save(shop, "natcash", mapOf("username" to "new"))

        manager.revoke(shop, "natcash", retired.id)
        manager.revoke(shop, "natcash", active.id)

        val listed = manager.providersOf(shop).single { it.provider.id == "natcash" }
        assertEquals(CredentialOrigin.NONE, listed.origin)
        assertEquals(listOf(CredentialStatus.REVOKED, CredentialStatus.REVOKED), listed.revisions.map { it.status })
        assertTrue(listed.revisions.none { it.sealed })
        assertTrue(store.secrets.isEmpty())
        assertEquals(AuditKind.PROVIDER_CREDENTIALS_REVOKED, audit.records.last().kind)
        assertEquals("natcash ${active.id}", audit.records.last().detail)
        assertEquals(ProblemType.STATE_CONFLICT, assertFailsWith<OpenFspException> { manager.revoke(shop, "natcash", active.id) }.type)
    }

    @Test
    fun `a revision is revoked only through its own account and provider`() {
        val shop = accounts.open("shop").id
        val other = accounts.open("other").id
        val revision = manager.save(shop, "natcash", mapOf("username" to "merchant"))
        val audited = audit.records.size

        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.revoke(other, "natcash", revision.id) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.revoke(shop, "moncash", revision.id) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.revoke(shop, "natcash", "cred_unknown") }.type)
        assertEquals(CredentialStatus.ACTIVE, store.revisions.getValue(revision.id).status)
        assertEquals(audited, audit.records.size)
    }

    @Test
    fun `a retired revision is wiped once no pending payment uses it and the creation lease is over`() {
        val shop = accounts.open("shop").id
        val used = manager.save(shop, "natcash", mapOf("username" to "used"))
        val unused = manager.save(shop, "natcash", mapOf("username" to "unused"))
        manager.save(shop, "natcash", mapOf("username" to "current"))
        store.pending += used.id

        assertEquals(0, manager.purgeRetired(10, 5))
        clock.advance(Duration.ofMinutes(6))
        assertEquals(1, manager.purgeRetired(10, 5))

        assertTrue(store.revisions.getValue(used.id).sealed)
        assertEquals(false, store.revisions.getValue(unused.id).sealed)
        assertNull(store.secrets[unused.id])
        assertEquals(3, store.revisions.size)
    }

    @Test
    fun `switching to the platform credentials retires the active revision, is audited and leaves other accounts untouched`() {
        val shop = accounts.open("shop").id
        val other = accounts.open("other").id
        val retired = manager.save(shop, "moncash", mapOf("client-id" to "a", "client-secret" to "b"))
        val kept = manager.save(other, "moncash", mapOf("client-id" to "c", "client-secret" to "d"))

        manager.switchMode(shop, "moncash", "platform", "req_switch")

        val listed = manager.providersOf(shop).single { it.provider.id == "moncash" }
        assertEquals(CredentialMode.PLATFORM to CredentialOrigin.PLATFORM, listed.mode to listed.origin)
        assertEquals(CredentialStatus.RETIRED, listed.revisions.single().status)
        assertTrue(listed.revisions.single().sealed)
        val record = audit.records.last()
        assertEquals(AuditKind.PROVIDER_CREDENTIAL_MODE_CHANGED, record.kind)
        assertEquals(listOf("admin", "req_switch", shop, "moncash own platform ${retired.id}"), listOf(record.principalId, record.requestId, record.result, record.detail))
        val untouched = manager.providersOf(other).single { it.provider.id == "moncash" }
        assertEquals(CredentialMode.OWN to CredentialStatus.ACTIVE, untouched.mode to untouched.revisions.single().status)
        assertEquals(kept.id, untouched.active?.id)
    }

    @Test
    fun `the own credentials come back only with an active revision, stored anew`() {
        val shop = accounts.open("shop").id
        manager.save(shop, "natcash", mapOf("username" to "first"))
        manager.switchMode(shop, "natcash", "platform", null)
        val audited = audit.records.size

        assertEquals(ProblemType.STATE_CONFLICT, assertFailsWith<OpenFspException> { manager.switchMode(shop, "natcash", "own", null) }.type)
        manager.switchMode(shop, "natcash", "platform", null)
        assertEquals(audited, audit.records.size)

        manager.save(shop, "natcash", mapOf("username" to "second"))
        val listed = manager.providersOf(shop).single { it.provider.id == "natcash" }
        assertEquals(CredentialMode.OWN to CredentialOrigin.ACCOUNT, listed.mode to listed.origin)
        manager.switchMode(shop, "natcash", "own", null)
        assertEquals(audited + 1, audit.records.size)
    }

    @Test
    fun `removing the own credentials keeps the account on them, without falling back to the platform`() {
        val shop = accounts.open("shop").id
        manager.save(shop, "moncash", mapOf("client-id" to "a", "client-secret" to "b"))
        manager.remove(shop, "moncash")

        val listed = manager.providersOf(shop).single { it.provider.id == "moncash" }
        assertEquals(CredentialMode.OWN to CredentialOrigin.NONE, listed.mode to listed.origin)
    }

    @Test
    fun `a mode switch is refused for an unknown mode, account or provider without account credentials`() {
        val shop = accounts.open("shop").id
        assertEquals(ProblemType.MISSING_FIELD, assertFailsWith<OpenFspException> { manager.switchMode(shop, "moncash", null, null) }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { manager.switchMode(shop, "moncash", "shared", null) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.switchMode("acct_unknown", "moncash", "platform", null) }.type)
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { manager.switchMode(shop, "openfsp", "platform", null) }.type)
        assertTrue(store.modes.isEmpty())
    }
}

package org.openfsp.gateway.auth

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.account.spi.stubs.InMemoryMerchantAccounts
import org.openfsp.gateway.auth.spi.stubs.InMemoryApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.openfsp.gateway.common.OpenFspException
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ApiKeyAuthorityTest {
    private val clock = TestClock()
    private val audit = InMemoryAuditTrail()
    private val accounts = InMemoryMerchantAccounts()
    private val service = ApiKeyAuthority(InMemoryApiKeys(), accounts, audit, BusinessCalendar(clock), GatewayEnvironment("test"), ImmediateTransactions())

    @Test
    fun `a presented key is logged by its environment and lookup prefix only`() {
        assertEquals("ofsp_test_${"A".repeat(12)}", PresentedKey.redact("ofsp_test_" + "A".repeat(40)))
        assertEquals("malformed", PresentedKey.redact("ofsp_test_short"))
    }

    @Test
    fun `an issued key authenticates with its scopes, capability discovery always included`() {
        val issued = service.issue(MerchantAccount.DEFAULT, "shop", setOf(Scope.PAYMENTS_WRITE))
        assertTrue(issued.secret.startsWith("ofsp_test_"))
        assertEquals(setOf(Scope.PAYMENTS_WRITE, Scope.CAPABILITIES_READ), service.authenticate(issued.secret)!!.scopes)
    }

    @Test
    fun `a key belongs to the account of its principal, and only an active account receives keys`() {
        accounts.insert(MerchantAccount("acct_shop", "shop", clock.instant()))
        accounts.insert(MerchantAccount("acct_lapsed", "lapsed", clock.instant(), expiresAt = clock.instant()))
        val issued = service.issue("acct_shop", "web", setOf(Scope.PAYMENTS_READ))
        val other = service.issue(MerchantAccount.DEFAULT, "web", setOf(Scope.PAYMENTS_READ))

        assertEquals("acct_shop", service.authenticate(issued.secret)!!.accountId)
        assertEquals(MerchantAccount.DEFAULT, service.authenticate(other.secret)!!.accountId)
        assertNotEquals(issued.principalId, other.principalId)
        listOf("acct_unknown", "acct_lapsed").forEach { account ->
            assertEquals("/account", assertFailsWith<OpenFspException>(account) { service.issue(account, "web", setOf(Scope.PAYMENTS_READ)) }.errors.single().field)
        }
    }

    @Test
    fun `revoked, expired, foreign and malformed keys are all rejected alike`() {
        val revoked = service.issue(MerchantAccount.DEFAULT, "shop", setOf(Scope.PAYMENTS_READ))
        service.revoke(revoked.keyId)
        val expiring = service.issue(MerchantAccount.DEFAULT, "shop", setOf(Scope.PAYMENTS_READ), expiresAt = clock.instant().plusSeconds(1))
        clock.advance(Duration.ofSeconds(2))
        listOf(revoked.secret, expiring.secret, "ofsp_live_" + "A".repeat(40), "ofsp_test_short", "nonsense").forEach { assertNull(service.authenticate(it), it) }
    }

    @Test
    fun `a registered key is registered once and never again after its revocation`() {
        val secret = "ofsp_test_" + "R".repeat(40)
        val keyId = service.register(MerchantAccount.DEFAULT, "development", secret, setOf(Scope.PAYMENTS_READ))
        assertEquals(keyId, service.register(MerchantAccount.DEFAULT, "development", secret, setOf(Scope.PAYMENTS_READ)))
        service.revoke(keyId!!)
        assertNull(service.register(MerchantAccount.DEFAULT, "development", secret, setOf(Scope.PAYMENTS_READ)))
        assertNull(service.authenticate(secret))
    }

    @Test
    fun `issuing and revoking keys is audited with scopes and without secrets`() {
        val issued = service.issue(MerchantAccount.DEFAULT, "shop", setOf(Scope.PAYMENTS_READ))
        service.revoke(issued.keyId)
        assertEquals(listOf(AuditKind.KEY_ISSUED, AuditKind.KEY_REVOKED), audit.records.map { it.kind })
        assertEquals(listOf("payments:read capabilities:read", "payments:read capabilities:read"), audit.records.map { it.detail })
        assertEquals(audit.records.first().lookupPrefix, audit.records.last().lookupPrefix)
        assertEquals(clock.instant(), audit.records.last().at)
        assertTrue(audit.records.none { issued.secret in it.toString() })
    }
}

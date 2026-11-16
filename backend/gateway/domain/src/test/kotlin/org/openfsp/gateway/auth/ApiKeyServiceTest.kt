package org.openfsp.gateway.auth

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.auth.spi.stubs.InMemoryApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyServiceTest {
    private val clock = TestClock()
    private val audit = InMemoryAuditTrail()
    private val service = ApiKeyService(InMemoryApiKeys(), audit, BusinessCalendar(clock), GatewayEnvironment("test"))

    @Test
    fun `an issued key authenticates with its scopes, capability discovery always included`() {
        val issued = service.issue("shop", setOf(Scope.PAYMENTS_WRITE))
        assertTrue(issued.secret.startsWith("ofsp_test_"))
        assertEquals(setOf(Scope.PAYMENTS_WRITE, Scope.CAPABILITIES_READ), service.authenticate(issued.secret)!!.scopes)
    }

    @Test
    fun `revoked, expired, foreign and malformed keys are all rejected alike`() {
        val revoked = service.issue("shop", setOf(Scope.PAYMENTS_READ))
        service.revoke(revoked.keyId)
        val expiring = service.issue("shop", setOf(Scope.PAYMENTS_READ), expiresAt = clock.instant().plusSeconds(1))
        clock.advance(java.time.Duration.ofSeconds(2))
        listOf(revoked.secret, expiring.secret, "ofsp_live_" + "A".repeat(40), "ofsp_test_short", "nonsense").forEach { assertNull(service.authenticate(it), it) }
    }

    @Test
    fun `a registered key is registered once and never again after its revocation`() {
        val secret = "ofsp_test_" + "R".repeat(40)
        val keyId = service.register("development", secret, setOf(Scope.PAYMENTS_READ))
        assertEquals(keyId, service.register("development", secret, setOf(Scope.PAYMENTS_READ)))
        service.revoke(keyId!!)
        assertNull(service.register("development", secret, setOf(Scope.PAYMENTS_READ)))
        assertNull(service.authenticate(secret))
    }

    @Test
    fun `issuing and revoking keys is audited with scopes and without secrets`() {
        val issued = service.issue("shop", setOf(Scope.PAYMENTS_READ))
        service.revoke(issued.keyId)
        assertEquals(listOf(AuditKind.KEY_ISSUED, AuditKind.KEY_REVOKED), audit.records.map { it.kind })
        assertEquals(listOf("payments:read capabilities:read", "payments:read capabilities:read"), audit.records.map { it.detail })
        assertEquals(audit.records.first().lookupPrefix, audit.records.last().lookupPrefix)
        assertEquals(clock.instant(), audit.records.last().at)
        assertTrue(audit.records.none { issued.secret in it.toString() })
    }
}

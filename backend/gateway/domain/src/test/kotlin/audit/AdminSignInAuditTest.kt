package org.openfsp.gateway.audit

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminSignInAuditTest {
    @Test
    fun `every sign-in attempt is audited with its outcome`() {
        val trail = InMemoryAuditTrail()
        val audit = AdminSignInAudit(trail, BusinessCalendar(TestClock()))
        audit.signIn(accepted = true, requestId = "req_1")
        audit.signIn(accepted = false, requestId = "req_2")
        assertEquals(listOf(AuditKind.ADMIN_SIGN_IN to "204", AuditKind.ADMIN_SIGN_IN_FAILURE to "401"), trail.records.map { it.kind to it.result })
        assertEquals(listOf("req_1", "req_2"), trail.records.map { it.requestId })
    }
}

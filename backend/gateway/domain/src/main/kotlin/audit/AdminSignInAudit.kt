package org.openfsp.gateway.audit

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.audit.api.AuditAdminSignIns
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar

@DomainService
class AdminSignInAudit(
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
) : AuditAdminSignIns {
    override fun signIn(
        accepted: Boolean,
        requestId: String?,
    ) = audit.record(
        AuditRecord(
            at = calendar.now(),
            kind = if (accepted) AuditKind.ADMIN_SIGN_IN else AuditKind.ADMIN_SIGN_IN_FAILURE,
            principalId = null,
            lookupPrefix = null,
            operation = OPERATION,
            requestId = requestId,
            result = if (accepted) ACCEPTED else REFUSED,
        ),
    )

    private companion object {
        const val OPERATION = "POST /admin/session"
        const val ACCEPTED = "204"
        const val REFUSED = "401"
    }
}

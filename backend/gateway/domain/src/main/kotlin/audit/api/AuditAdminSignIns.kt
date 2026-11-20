package org.openfsp.gateway.audit.api

interface AuditAdminSignIns {
    fun signIn(
        accepted: Boolean,
        requestId: String?,
    )
}

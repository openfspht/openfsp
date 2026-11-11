package org.openfsp.gateway.audit.spi

import org.openfsp.gateway.audit.AuditRecord

interface AuditTrail {
    fun record(record: AuditRecord)
}

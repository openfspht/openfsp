package org.openfsp.gateway.audit.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail

@Stub
class InMemoryAuditTrail : AuditTrail {
    val records = mutableListOf<AuditRecord>()

    override fun record(record: AuditRecord) {
        records += record
    }

    override fun latest(limit: Int): List<AuditRecord> = records.reversed().take(limit)
}

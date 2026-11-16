package org.openfsp.validator.attestation.spi

import org.openfsp.validator.attestation.StoredAttestation
import java.util.UUID

interface AttestationStore {
    fun saveOrExisting(attestation: StoredAttestation): StoredAttestation

    fun find(id: UUID): StoredAttestation?

    fun findByReport(reportId: UUID): StoredAttestation?
}

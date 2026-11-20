package org.openfsp.validator.attestation.api

import org.openfsp.validator.attestation.AttestationKey
import org.openfsp.validator.attestation.AttestationVerification
import org.openfsp.validator.attestation.IssuedAttestation
import java.util.UUID

interface IssueAttestations {
    fun issue(runId: UUID): IssuedAttestation
}

interface ReadAttestations {
    val publicUrl: String

    fun find(id: UUID): IssuedAttestation

    fun forRun(runId: UUID): IssuedAttestation

    fun pageUrl(id: UUID): String

    fun publishedKeys(): List<AttestationKey>
}

interface VerifyAttestations {
    fun verify(id: UUID): AttestationVerification
}

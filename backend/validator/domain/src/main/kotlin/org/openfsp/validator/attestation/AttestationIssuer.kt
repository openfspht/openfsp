package org.openfsp.validator.attestation

import org.openfsp.ddd.DomainService
import org.openfsp.validator.attestation.api.IssueAttestations
import org.openfsp.validator.attestation.api.ReadAttestations
import org.openfsp.validator.attestation.api.VerifyAttestations
import org.openfsp.validator.attestation.spi.AttestationFormat
import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.attestation.spi.AttestationStore
import org.openfsp.validator.attestation.spi.SigningKeys
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.report.spi.ReportStore
import org.openfsp.validator.run.api.RunState
import org.openfsp.validator.run.api.RunStates
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class StoredAttestation(
    val id: UUID,
    val reportId: UUID,
    val issuedAt: Instant,
    val kid: String,
    val jws: String,
)

class IssuedAttestation(
    val stored: StoredAttestation,
    val payload: ByteArray,
    read: (ByteArray) -> Attestation,
    val created: Boolean = false,
) {
    val document: Attestation by lazy { read(payload) }
}

data class AttestationSettings(
    val publicUrl: String,
)

@DomainService
class AttestationIssuer(
    private val attestations: AttestationStore,
    private val reports: ReportStore,
    private val reportFormat: ReportFormat,
    private val keys: SigningKeys,
    private val signatures: AttestationSignatures,
    private val format: AttestationFormat,
    json: JsonCodec,
    private val runs: RunStates,
    private val settings: AttestationSettings,
    private val clock: Clock,
) : IssueAttestations,
    ReadAttestations,
    VerifyAttestations {
    private val verifier = AttestationVerifier(keys::find, signatures, json, reportFormat)

    override val publicUrl: String get() = settings.publicUrl.trimEnd('/')

    override fun issue(runId: UUID): IssuedAttestation {
        when (runs.state(runId)) {
            RunState.PREPARED -> throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start the run and wait for its summary before issuing an attestation.")
            RunState.RUNNING -> throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Wait for the summary of run $runId before issuing an attestation.")
            RunState.ENDED -> Unit
        }
        attestations.findByReport(runId)?.let { return decoded(it) }
        val stored = reports.find(runId) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No finished run $runId: only a run that produced a report can be attested.")
        val key = keys.signing() ?: throw ValidatorException(ProblemKind.ATTESTATION_UNAVAILABLE, "This validator has no attestation key.")
        val id = UUID.randomUUID()
        val now = clock.instant()
        val digest = reportFormat.digest(stored.body) ?: throw IllegalStateException("report $runId is not a JSON document")
        val document = Attestation.of(id, publicUrl, now, runId, reportFormat.read(stored.body), digest)
        val saved = attestations.saveOrExisting(StoredAttestation(id, runId, now, key.kid, signatures.sign(format.canonical(document), key)))
        return decoded(saved, created = saved.id == id)
    }

    override fun find(id: UUID): IssuedAttestation = attestations.find(id)?.let(::decoded) ?: throw notFound(id)

    override fun forRun(runId: UUID): IssuedAttestation = attestations.findByReport(runId)?.let(::decoded) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "Run $runId has no attestation yet.")

    override fun verify(id: UUID): AttestationVerification {
        val stored = attestations.find(id) ?: throw notFound(id)
        return verifier.verify(stored.jws, reports.find(stored.reportId)?.body, id.toString(), stored.reportId.toString())
    }

    override fun pageUrl(id: UUID): String = "$publicUrl/attestations/$id"

    override fun publishedKeys(): List<AttestationKey> = keys.published()

    private fun decoded(
        stored: StoredAttestation,
        created: Boolean = false,
    ): IssuedAttestation {
        val payload = signatures.parse(stored.jws)?.payload ?: throw IllegalStateException("attestation ${stored.id} holds no compact JWS")
        return IssuedAttestation(stored, payload, format::read, created)
    }

    private fun notFound(id: UUID) = ValidatorException(ProblemKind.NOT_FOUND, "No attestation $id.")
}

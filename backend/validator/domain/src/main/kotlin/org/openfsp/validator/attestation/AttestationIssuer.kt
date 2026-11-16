package org.openfsp.validator.attestation

import org.openfsp.ddd.DomainService
import org.openfsp.validator.attestation.api.IssueAttestations
import org.openfsp.validator.attestation.api.ReadAttestations
import org.openfsp.validator.attestation.api.VerifyAttestations
import org.openfsp.validator.attestation.spi.AttestationSignatures
import org.openfsp.validator.attestation.spi.AttestationStore
import org.openfsp.validator.attestation.spi.SigningKeys
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.CanonicalJson
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.report.spi.ReportStore
import org.openfsp.validator.run.PreparedRuns
import org.openfsp.validator.run.RunSessions
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.readValue
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
    val payload: JsonNode,
    val created: Boolean = false,
) {
    val document: Attestation by lazy { Reports.json.treeToValue(payload, Attestation::class.java) }
}

data class AttestationSettings(
    val publicUrl: String,
)

@DomainService
class AttestationIssuer(
    private val attestations: AttestationStore,
    private val reports: ReportStore,
    private val keys: SigningKeys,
    private val signatures: AttestationSignatures,
    private val prepared: PreparedRuns,
    private val sessions: RunSessions,
    private val settings: AttestationSettings,
    private val clock: Clock,
) : IssueAttestations,
    ReadAttestations,
    VerifyAttestations {
    private val verifier = AttestationVerifier(keys::find, signatures)

    override val publicUrl: String get() = settings.publicUrl.trimEnd('/')

    override fun issue(runId: UUID): IssuedAttestation {
        if (prepared.isWaiting(runId)) throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start the run and wait for its summary before issuing an attestation.")
        if (sessions.find(runId)?.finished == false) throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Wait for the summary of run $runId before issuing an attestation.")
        attestations.findByReport(runId)?.let { return decoded(it) }
        val stored = reports.find(runId) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No finished run $runId: only a run that produced a report can be attested.")
        val key = keys.signing() ?: throw ValidatorException(ProblemKind.ATTESTATION_UNAVAILABLE, "This validator has no attestation key.")
        val id = UUID.randomUUID()
        val now = clock.instant()
        val document = Attestation.of(id, publicUrl, now, runId, Reports.json.readValue<Report>(stored.body), stored.digest)
        val payload = CanonicalJson.of(Reports.json.valueToTree(document)).toByteArray(Charsets.UTF_8)
        val saved = attestations.saveOrExisting(StoredAttestation(id, runId, now, key.kid, signatures.sign(payload, key)))
        return IssuedAttestation(saved, decode(saved), created = saved.id == id)
    }

    override fun find(id: UUID): IssuedAttestation = attestations.find(id)?.let(::decoded) ?: throw notFound(id)

    override fun forRun(runId: UUID): IssuedAttestation = attestations.findByReport(runId)?.let(::decoded) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "Run $runId has no attestation yet.")

    override fun verify(id: UUID): AttestationVerification {
        val stored = attestations.find(id) ?: throw notFound(id)
        return verifier.verify(stored.jws, reports.find(stored.reportId)?.body, id.toString(), stored.reportId.toString())
    }

    override fun pageUrl(id: UUID): String = "$publicUrl/attestations/$id"

    override fun publishedKeys(): List<AttestationKey> = keys.published()

    private fun decoded(stored: StoredAttestation) = IssuedAttestation(stored, decode(stored))

    private fun decode(stored: StoredAttestation): JsonNode {
        val payload = signatures.parse(stored.jws)?.payload ?: throw IllegalStateException("attestation ${stored.id} holds no compact JWS")
        return Reports.json.readTree(payload)
    }

    private fun notFound(id: UUID) = ValidatorException(ProblemKind.NOT_FOUND, "No attestation $id.")
}

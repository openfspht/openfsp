package org.openfsp.validator.web.attestations

import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.openfsp.validator.web.reports.CanonicalJson
import org.openfsp.validator.web.reports.ReportStore
import org.openfsp.validator.web.runs.PreparedRuns
import org.openfsp.validator.web.runs.RunSessions
import org.springframework.stereotype.Service
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

interface AttestationStore {
    fun saveOrExisting(attestation: StoredAttestation): StoredAttestation

    fun find(id: UUID): StoredAttestation?

    fun findByReport(reportId: UUID): StoredAttestation?
}

class IssuedAttestation(
    val stored: StoredAttestation,
    val payload: JsonNode,
    val created: Boolean = false,
) {
    val document: Attestation by lazy { Reports.json.treeToValue(payload, Attestation::class.java) }
}

@Service
class AttestationService(
    private val attestations: AttestationStore,
    private val reports: ReportStore,
    private val keys: AttestationKeys,
    private val prepared: PreparedRuns,
    private val sessions: RunSessions,
    private val properties: ValidatorProperties,
    private val clock: Clock,
) {
    private val verifier = AttestationVerifier(keys::find)

    val publicUrl: String get() = properties.attestation.publicUrl.trimEnd('/')

    fun issue(runId: UUID): IssuedAttestation {
        if (prepared.isWaiting(runId)) throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start the run and wait for its summary before issuing an attestation.")
        if (sessions.find(runId)?.finished == false) throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Wait for the summary of run $runId before issuing an attestation.")
        attestations.findByReport(runId)?.let { return decoded(it) }
        val stored = reports.find(runId) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No finished run $runId: only a run that produced a report can be attested.")
        val key = keys.signing() ?: throw ValidatorException(ProblemKind.ATTESTATION_UNAVAILABLE, "This validator has no attestation key.")
        val id = UUID.randomUUID()
        val now = clock.instant()
        val document = Attestation.of(id, publicUrl, now, runId, Reports.json.readValue<Report>(stored.body), stored.digest)
        val payload = CanonicalJson.of(Reports.json.valueToTree(document)).toByteArray(Charsets.UTF_8)
        val saved = attestations.saveOrExisting(StoredAttestation(id, runId, now, key.kid, CompactJws.sign(payload, key)))
        return IssuedAttestation(saved, decode(saved), created = saved.id == id)
    }

    fun find(id: UUID): IssuedAttestation = attestations.find(id)?.let(::decoded) ?: throw notFound(id)

    fun forRun(runId: UUID): IssuedAttestation = attestations.findByReport(runId)?.let(::decoded) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "Run $runId has no attestation yet.")

    fun verify(id: UUID): AttestationVerification {
        val stored = attestations.find(id) ?: throw notFound(id)
        return verifier.verify(stored.jws, reports.find(stored.reportId)?.body, id.toString(), stored.reportId.toString())
    }

    fun pageUrl(id: UUID): String = "$publicUrl/attestations/$id"

    private fun decoded(stored: StoredAttestation) = IssuedAttestation(stored, decode(stored))

    private fun decode(stored: StoredAttestation): JsonNode {
        val payload = CompactJws.parse(stored.jws)?.payload ?: throw IllegalStateException("attestation ${stored.id} holds no compact JWS")
        return Reports.json.readTree(payload)
    }

    private fun notFound(id: UUID) = ValidatorException(ProblemKind.NOT_FOUND, "No attestation $id.")
}

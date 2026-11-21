package org.openfsp.validator.attestation

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.report.Claim
import org.openfsp.validator.report.ProfileResult
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.Requirement
import java.time.Instant
import java.util.UUID

data class Attestation(
    val type: String,
    val id: String,
    val issuer: String,
    val issuedAt: String,
    val statement: String,
    val claim: Claim,
    val suite: SuiteVersion,
    val target: AttestedTarget,
    val run: AttestedRun,
    val configuration: Map<String, Any>,
    val verdicts: AttestedVerdicts,
    val report: AttestedReport,
    val limits: List<Limit>,
    val validity: Validity,
) {
    companion object {
        const val TYPE = "openfsp-conformance-attestation/1"
        const val STATEMENT =
            "This attestation records the results of one run of the OpenFSP conformance suite against the target below, " +
                "including failures. It does not certify an implementation (conformance 7.6)."
        const val VALIDITY =
            "Valid for this configuration and date; stale when the implementation or protocol version changes."
        const val VALIDITY_REFERENCE = "conformance#6.4"
        const val DIGEST_ALGORITHM = "sha-256"
        const val CANONICALIZATION = "RFC 8785"

        fun of(
            id: UUID,
            issuer: String,
            issuedAt: Instant,
            reportId: UUID,
            report: Report,
            digest: String,
        ) = Attestation(
            type = TYPE,
            id = id.toString(),
            issuer = issuer,
            issuedAt = issuedAt.toString(),
            statement = STATEMENT,
            claim = report.claim,
            suite = report.suite,
            target = AttestedTarget(report.claim.target, report.configuration[Configuration.BASE_URL] as? String ?: "", report.targetProtocolVersion),
            run = AttestedRun(reportId.toString(), report.date, report.durationMs),
            configuration = report.configuration.mapNotNull { (name, value) -> value?.let { name to it } }.toMap(),
            verdicts = AttestedVerdicts(report.core, report.profiles, coreResultsOf(report)),
            report = AttestedReport(reportId.toString(), DIGEST_ALGORITHM, CANONICALIZATION, digest),
            limits = report.limits,
            validity = Validity(VALIDITY, VALIDITY_REFERENCE),
        )
    }
}

data class AttestedTarget(
    val kind: String,
    val baseUrl: String,
    val protocolVersion: String?,
)

data class AttestedRun(
    val id: String,
    val date: String,
    val durationMs: Long,
)

data class AttestedVerdicts(
    val core: LevelVerdict,
    val profiles: List<ProfileResult>,
    val coreResults: Map<Verdict, Int> = emptyMap(),
)

private fun coreResultsOf(report: Report): Map<Verdict, Int> =
    report.results
        .filter { it.level == Requirement.CORE }
        .groupingBy { it.verdict }
        .eachCount()
        .toSortedMap()

data class AttestedReport(
    val id: String,
    val digestAlgorithm: String,
    val canonicalization: String,
    val digest: String,
)

data class Validity(
    val statement: String,
    val reference: String,
)

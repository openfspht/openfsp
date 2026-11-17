package org.openfsp.validator.report

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.RedactedExchange
import org.openfsp.validator.engine.RunResult
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Suite
import java.time.Duration

data class Report(
    val claim: Claim,
    val suite: SuiteVersion,
    val targetProtocolVersion: String?,
    val date: String,
    val durationMs: Long,
    val configuration: Map<String, Any?>,
    val core: LevelVerdict,
    val profiles: List<ProfileResult>,
    val results: List<ResultEntry>,
    val limits: List<Limit>,
)

data class Claim(
    val target: String,
    val level: String,
    val profiles: List<String>,
    val protocolVersion: String,
)

data class SuiteVersion(
    val version: String,
    val protocolVersion: String,
)

data class ProfileResult(
    val name: String,
    val verdict: LevelVerdict,
)

data class ResultEntry(
    val scenario: String,
    val title: String,
    val requirementId: String,
    val requirement: String,
    val level: String,
    val finding: String? = null,
    val verdict: Verdict,
    val assisted: Boolean,
    val action: String?,
    val reason: String?,
    val failure: FailureEntry?,
    val observations: List<String>? = null,
)

data class FailureEntry(
    val step: String,
    val mismatches: List<MismatchEntry>,
    val exchange: RedactedExchange?,
)

data class MismatchEntry(
    val expectation: String,
    val expected: String,
    val observed: String,
)

object Reports {
    const val NO_LEVEL = "none"

    fun of(
        suite: Suite,
        configuration: Configuration,
        run: RunResult,
    ): Report {
        val core = LevelVerdict.of(run.results.filter { it.requirement.isCore }).let { if (configuration.only != null && it == LevelVerdict.PASS) LevelVerdict.INCOMPLETE else it }
        val profiles =
            run.results
                .filterNot { it.requirement.isCore }
                .groupBy { it.requirement.level }
                .map { (name, results) -> ProfileResult(name, LevelVerdict.of(results)) }
                .sortedBy { it.name }
        val claimed = if (core == LevelVerdict.PASS) profiles.filter { it.verdict == LevelVerdict.PASS }.map { it.name } else emptyList()
        return Report(
            claim =
                Claim(
                    target = configuration.target.wire,
                    level = if (core == LevelVerdict.PASS) Requirement.CORE else NO_LEVEL,
                    profiles = claimed,
                    protocolVersion = suite.catalogue.suite.protocolVersion,
                ),
            suite = SuiteVersion(suite.catalogue.suite.version, suite.catalogue.suite.protocolVersion),
            targetProtocolVersion = run.targetProtocolVersion,
            date = run.startedAt.toString(),
            durationMs = Duration.between(run.startedAt, run.finishedAt).toMillis(),
            configuration = configuration.redacted(suite.catalogue.suite.version),
            core = core,
            profiles = profiles,
            results = run.results.map(::entry),
            limits = suite.catalogue.limits,
        )
    }

    fun entry(result: ScenarioResult): ResultEntry =
        ResultEntry(
            scenario = result.scenario.id,
            title = result.scenario.title,
            requirementId = result.requirement.id,
            requirement = result.requirement.requirement,
            level = result.requirement.level,
            finding = result.requirement.finding,
            verdict = result.verdict,
            assisted = result.scenario.assisted != null || result.verdict == Verdict.ASSISTED,
            action = result.scenario.assisted?.action ?: result.reason.takeIf { result.verdict == Verdict.ASSISTED },
            reason = result.reason,
            failure =
                result.failure?.let { failure ->
                    FailureEntry(failure.step, failure.mismatches.map { MismatchEntry(it.expectation, it.expected, it.observed) }, failure.exchange)
                },
            observations = result.observations.ifEmpty { null },
        )

    fun summary(report: Report): String =
        buildString {
            appendLine("OpenFSP conformance suite ${report.suite.version}, protocol ${report.suite.protocolVersion}")
            appendLine("Target ${report.claim.target} at ${report.configuration[Configuration.BASE_URL]}, announcing protocol ${report.targetProtocolVersion ?: "(unknown)"}")
            appendLine()
            report.results.forEach { result ->
                appendLine("  %-15s %-12s %s".format(result.verdict, result.requirementId, result.title))
                result.reason?.let { appendLine("                  $it") }
                result.observations?.forEach { appendLine("                  $it") }
                result.failure?.let { failure ->
                    failure.mismatches.forEach { appendLine("                  ${failure.step}: ${it.expectation}: expected ${it.expected}, observed ${it.observed.take(SUMMARY_LIMIT)}") }
                }
            }
            appendLine()
            appendLine("Core: ${report.core}")
            report.profiles.forEach { appendLine("Profile ${it.name}: ${it.verdict}") }
            appendLine("Claim: ${report.claim.target}, ${report.claim.level}${report.claim.profiles.joinToString("") { " + $it" }}, protocol ${report.claim.protocolVersion}")
            appendLine()
            report.limits.forEach { appendLine("Limit (${it.reference}): ${it.text}") }
        }

    private const val SUMMARY_LIMIT = 300
}

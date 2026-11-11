package org.openfsp.validator.report

import com.fasterxml.jackson.annotation.JsonInclude
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.RedactedExchange
import org.openfsp.validator.engine.RunResult
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Suite
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

@JsonInclude(JsonInclude.Include.NON_NULL)
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

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResultEntry(
    val scenario: String,
    val title: String,
    val requirementId: String,
    val requirement: String,
    val level: String,
    val verdict: Verdict,
    val assisted: Boolean,
    val action: String?,
    val reason: String?,
    val failure: FailureEntry?,
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

    val json: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build()

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
            configuration = configuration.redacted(),
            core = core,
            profiles = profiles,
            results = run.results.map(::entry),
            limits = suite.catalogue.limits,
        )
    }

    private fun entry(result: ScenarioResult): ResultEntry =
        ResultEntry(
            scenario = result.scenario.id,
            title = result.scenario.title,
            requirementId = result.requirement.id,
            requirement = result.requirement.requirement,
            level = result.requirement.level,
            verdict = result.verdict,
            assisted = result.scenario.assisted != null,
            action = result.scenario.assisted?.action,
            reason = result.reason,
            failure =
                result.failure?.let { failure ->
                    FailureEntry(failure.step, failure.mismatches.map { MismatchEntry(it.expectation, it.expected, it.observed) }, failure.exchange)
                },
        )

    fun summary(report: Report): String =
        buildString {
            appendLine("OpenFSP conformance suite ${report.suite.version}, protocol ${report.suite.protocolVersion}")
            appendLine("Target ${report.claim.target} at ${report.configuration["base_url"]}, announcing protocol ${report.targetProtocolVersion ?: "(unknown)"}")
            appendLine()
            report.results.forEach { result ->
                appendLine("  %-15s %-12s %s".format(result.verdict, result.requirementId, result.title))
                result.reason?.let { appendLine("                  $it") }
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

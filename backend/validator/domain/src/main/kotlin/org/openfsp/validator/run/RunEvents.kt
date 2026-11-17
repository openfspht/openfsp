package org.openfsp.validator.run

import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.report.Claim
import org.openfsp.validator.report.ProfileResult
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.ResultEntry
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import java.util.UUID

data class PlannedScenario(
    val index: Int,
    val scenario: String,
    val title: String,
    val area: String,
    val requirementId: String,
    val requirement: String,
    val level: String,
    val assisted: Boolean,
)

data class RunStartedEvent(
    val id: UUID,
    val target: String,
    val baseUrl: String,
    val suite: SuiteVersion,
    val total: Int,
    val scenarios: List<PlannedScenario>,
    val limits: List<Limit>,
)

data class ScenarioStartedEvent(
    val total: Int,
    val scenario: PlannedScenario,
)

data class ScenarioFinishedEvent(
    val index: Int,
    val total: Int,
    val result: ResultEntry,
)

data class RunSummaryEvent(
    val reportId: UUID,
    val core: LevelVerdict,
    val profiles: List<ProfileResult>,
    val claim: Claim,
    val targetProtocolVersion: String?,
    val durationMs: Long,
    val limits: List<Limit>,
)

data class RunEvent(
    val id: Long,
    val name: String,
    val payload: Any,
) {
    val terminal: Boolean get() = name == RunEvents.SUMMARY || name == RunEvents.FAILED
}

object RunEvents {
    const val RUN = "run"
    const val SCENARIO_STARTED = "scenario-started"
    const val SCENARIO_FINISHED = "scenario-finished"
    const val SUMMARY = "summary"
    const val FAILED = "failed"

    fun planned(
        index: Int,
        scenario: Scenario,
        requirement: Requirement,
    ) = PlannedScenario(index, scenario.id, scenario.title, area(scenario.id), requirement.id, requirement.requirement, requirement.level, scenario.assisted != null)

    fun planned(
        index: Int,
        entry: ResultEntry,
    ) = PlannedScenario(index, entry.scenario, entry.title, area(entry.scenario), entry.requirementId, entry.requirement, entry.level, entry.assisted)

    fun summary(
        id: UUID,
        report: Report,
    ) = RunSummaryEvent(id, report.core, report.profiles, report.claim, report.targetProtocolVersion, report.durationMs, report.limits)

    fun replay(
        id: UUID,
        baseUrl: String,
        report: Report,
    ): List<Pair<String, Any>> {
        val scenarios = report.results.mapIndexed { position, entry -> planned(position + 1, entry) }
        val total = scenarios.size
        val started = RunStartedEvent(id, report.claim.target, baseUrl, report.suite, total, scenarios, report.limits)
        return listOf(RUN to started) +
            report.results.mapIndexed { position, entry -> SCENARIO_FINISHED to ScenarioFinishedEvent(position + 1, total, entry) } +
            listOf(SUMMARY to summary(id, report))
    }

    private fun area(scenario: String): String = scenario.substringBefore('/', "")
}

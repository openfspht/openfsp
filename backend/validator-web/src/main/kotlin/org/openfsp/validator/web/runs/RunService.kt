package org.openfsp.validator.web.runs

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Engine
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ProblemBody
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.openfsp.validator.web.reports.ReportStore
import org.openfsp.validator.web.reports.StoredReport
import org.openfsp.validator.web.suite.ConformanceSuite
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ExecutorService

@Service
class RunService(
    private val conformance: ConformanceSuite,
    private val properties: ValidatorProperties,
    private val requests: RunRequests,
    private val slots: RunSlots,
    private val limiter: RunRateLimiter,
    private val sessions: RunSessions,
    private val prepared: PreparedRuns,
    private val spaces: SimulatorSpaces,
    private val reports: ReportStore,
    private val clock: Clock,
    private val runExecutor: ExecutorService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun prepare(
        request: PrepareRequest,
        client: String,
    ): PreparedRun {
        val target = requests.target(request)
        limiter.acquire(client)
        return prepared.prepare(target, spaces.open())
    }

    fun start(
        id: UUID,
        request: RunRequest,
    ) {
        if (prepared.isStarted(id)) throw ValidatorException(ProblemKind.RUN_STARTED, "Follow /api/runs/$id/events until the summary.")
        prepared.starting(id) { run -> launch(run, request) } ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No prepared run $id.")
    }

    private fun launch(
        run: PreparedRun,
        request: RunRequest,
    ) {
        val requested = requests.configuration(request, run.target)
        val slot = slots.acquire() ?: throw ValidatorException(ProblemKind.BUSY, "This validator runs ${slots.capacity} validations at a time. Try again in a few minutes.")
        try {
            val configuration = requested.configuration.copy(simulatorUrl = run.space.controlUrl.takeIf { requested.configuration.target == TargetKind.GATEWAY })
            val session = sessions.find(run.id) ?: sessions.open(run.id)
            val engine = Engine(conformance.suite, configuration, Progress(session))
            val plan = engine.plan().mapIndexed { position, scenario -> RunEvents.planned(position + 1, scenario, requirement(scenario)) }
            session.publish(RunEvents.RUN, RunStartedEvent(run.id, configuration.target.wire, configuration.baseUrl, suiteVersion(), plan.size, plan, conformance.suite.catalogue.limits))
            runExecutor.execute {
                slot.use { execute(run.id, engine, configuration, session) }
            }
        } catch (exception: RuntimeException) {
            slot.close()
            throw exception
        }
    }

    private fun execute(
        id: UUID,
        engine: Engine,
        configuration: Configuration,
        session: RunSession,
    ) {
        try {
            val run = engine.run()
            val report = public(Reports.of(conformance.suite, configuration, run))
            reports.save(
                StoredReport(
                    id = id,
                    createdAt = clock.instant(),
                    target = configuration.target.wire,
                    baseUrl = configuration.baseUrl,
                    core = report.core.name,
                    suiteVersion = report.suite.version,
                    body = Reports.json.writeValueAsString(report),
                ),
            )
            session.publish(RunEvents.SUMMARY, RunEvents.summary(id, report))
            session.finish(clock.instant())
        } catch (exception: VersionRefusedException) {
            fail(session, ProblemKind.VERSION_REFUSED, exception.message)
        } catch (exception: Exception) {
            log.error("run {} stopped", id, exception)
            fail(session, ProblemKind.RUN_FAILED, "The run stopped on an internal error.")
        }
    }

    private fun fail(
        session: RunSession,
        kind: ProblemKind,
        detail: String?,
    ) {
        val problem = ProblemBody.of(kind, detail, "/api/runs/${session.id}")
        session.publish(RunEvents.FAILED, problem)
        session.finish(clock.instant(), problem)
    }

    private fun public(report: Report): Report =
        report.copy(
            configuration =
                report.configuration +
                    mapOf(
                        "simulator_url" to properties.simulator.publicUrl.takeIf { report.configuration["simulator_url"] != null },
                        "conformance" to "openfsp conformance suite ${report.suite.version}",
                    ),
        )

    private fun suiteVersion() = SuiteVersion(conformance.suite.catalogue.suite.version, conformance.suite.catalogue.suite.protocolVersion)

    private fun requirement(scenario: Scenario): Requirement = conformance.suite.requirement(scenario.requirement)!!

    private inner class Progress(
        private val session: RunSession,
    ) : RunListener {
        override fun started(
            index: Int,
            total: Int,
            scenario: Scenario,
            requirement: Requirement,
        ) = session.publish(RunEvents.SCENARIO_STARTED, ScenarioStartedEvent(total, RunEvents.planned(index, scenario, requirement)))

        override fun finished(
            index: Int,
            total: Int,
            result: ScenarioResult,
        ) = session.publish(RunEvents.SCENARIO_FINISHED, ScenarioFinishedEvent(index, total, Reports.entry(result)))
    }
}

package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.engine.api.RunSuite
import org.openfsp.validator.problem.ProblemBody
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.report.StoredReport
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.report.spi.ReportStore
import org.openfsp.validator.run.api.FollowRuns
import org.openfsp.validator.run.api.PrepareRuns
import org.openfsp.validator.run.api.StartRuns
import org.openfsp.validator.run.api.SweepRuns
import org.openfsp.validator.run.spi.RunAlerts
import org.openfsp.validator.run.spi.RunEventStream
import org.openfsp.validator.run.spi.SimulatorSpace
import org.openfsp.validator.run.spi.SimulatorSpaces
import org.openfsp.validator.run.spi.TargetConnection
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.SuiteSource
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ExecutorService

@DomainService
class ConformanceRuns(
    private val source: SuiteSource,
    private val settings: RunSettings,
    private val runner: RunSuite,
    private val requests: RunRequests,
    private val slots: RunSlots,
    private val limiter: RunRateLimiter,
    private val sessions: RunSessions,
    private val prepared: PreparedRuns,
    private val spaces: SimulatorSpaces,
    private val webhooks: RunWebhooks,
    private val reports: ReportStore,
    private val alerts: RunAlerts,
    private val clock: Clock,
    private val runExecutor: ExecutorService,
) : PrepareRuns,
    StartRuns,
    FollowRuns,
    SweepRuns {
    override fun prepare(
        target: String?,
        client: String,
    ): PreparedRun {
        val kind = requests.target(target)
        limiter.acquire(client)
        return prepared.prepare(kind, spaces.open()).also { webhooks.open(it.id) }
    }

    override fun find(id: UUID): PreparedRun? = prepared.find(id)

    override fun webhookUrl(id: UUID): String = webhooks.urlOf(id)

    override fun start(
        id: UUID,
        order: RunOrder,
    ) {
        if (prepared.isStarted(id)) throw ValidatorException(ProblemKind.RUN_STARTED, "Follow $BASE/$id/events until the summary.")
        prepared.starting(id) { run -> launch(run, order) } ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No prepared run $id.")
    }

    override fun follow(
        id: UUID,
        lastEventId: Long?,
        stream: RunEventStream,
    ) {
        sessions.find(id)?.let { return it.subscribe(lastEventId, stream) }
        val report = reports.find(id)?.let { Reports.json.readValue<Report>(it.body) } ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No run $id.")
        val replay = sessions.transient(id)
        RunEvents.replay(id, report).forEach { (name, payload) -> replay.publish(name, payload) }
        replay.finish(clock.instant())
        replay.subscribe(lastEventId, stream)
    }

    override fun report(id: UUID): String {
        if (prepared.isWaiting(id)) throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start it with POST $BASE/$id/start.")
        val session = sessions.find(id)
        if (session != null && !session.finished) throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Follow $BASE/$id/events until the summary.")
        reports.find(id)?.let { return it.body }
        session?.failure?.let { failure -> throw ValidatorException(ProblemKind.entries.first { it.type == failure.type }, failure.detail) }
        throw ValidatorException(ProblemKind.NOT_FOUND, "No report for run $id.")
    }

    override fun sweep() {
        sessions.forgetFinished()
        prepared.forgetExpired()
        webhooks.forgetEnded()
    }

    private fun launch(
        run: PreparedRun,
        order: RunOrder,
    ) {
        val requested = requests.configuration(order, run.target)
        val connection: TargetConnection = requested.connection
        val slot =
            slots.acquire() ?: run {
                connection.close()
                throw ValidatorException(ProblemKind.BUSY, "This validator runs ${slots.capacity} validations at a time. Try again in a few minutes.")
            }
        try {
            val configuration =
                requested.configuration.copy(
                    simulatorUrl = run.space.controlUrl.takeIf { drivesItsSpace(requested.configuration, run.space) },
                    inputs = publishedInputs(requested.configuration, run.space) + requested.configuration.inputs,
                    webhookInbox = webhooks.open(run.id),
                )
            val session = sessions.find(run.id) ?: sessions.open(run.id)
            val suite = source.suite()
            val plan = runner.plan(suite, configuration).mapIndexed { position, planned -> RunEvents.planned(position + 1, planned.scenario, planned.requirement) }
            session.publish(RunEvents.RUN, RunStartedEvent(run.id, configuration.target.wire, configuration.baseUrl, suiteVersion(), plan.size, plan, suite.catalogue.limits))
            runExecutor.execute {
                try {
                    slot.use { connection.use { execute(run.id, configuration, connection, session) } }
                } finally {
                    spaces.close(run.space)
                }
            }
        } catch (exception: RuntimeException) {
            connection.close()
            slot.close()
            throw exception
        }
    }

    private fun execute(
        id: UUID,
        configuration: Configuration,
        connection: TargetConnection,
        session: RunSession,
    ) {
        try {
            val report = public(runner.run(source.suite(), configuration, connection, Progress(session)))
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
            alerts.stopped(id, exception)
            fail(session, ProblemKind.RUN_FAILED, "The run stopped on an internal error.")
        }
    }

    private fun fail(
        session: RunSession,
        kind: ProblemKind,
        detail: String?,
    ) {
        val problem = ProblemBody.of(kind, detail, "$BASE/${session.id}")
        session.publish(RunEvents.FAILED, problem)
        session.finish(clock.instant(), problem)
    }

    private fun public(report: Report): Report =
        report.copy(
            configuration =
                report.configuration +
                    mapOf(
                        "simulator_url" to settings.simulator.publicUrl.takeIf { report.configuration["simulator_url"] != null },
                        "conformance" to "openfsp conformance suite ${report.suite.version}",
                    ),
        )

    private fun suiteVersion(): SuiteVersion {
        val suite = source.suite()
        return SuiteVersion(suite.catalogue.suite.version, suite.catalogue.suite.protocolVersion)
    }

    private fun drivesItsSpace(
        configuration: Configuration,
        space: SimulatorSpace,
    ): Boolean = configuration.baseUrl.startsWith(space.publicUrl.trimEnd('/') + "/")

    private fun publishedInputs(
        configuration: Configuration,
        space: SimulatorSpace,
    ): Map<String, String> {
        val base = space.publicUrl.trimEnd('/')
        return settings.simulator.providers.values
            .firstOrNull { base + it.basePath == configuration.baseUrl }
            ?.nativeInputs
            .orEmpty()
    }

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

    private companion object {
        const val BASE = "/api/runs"
    }
}

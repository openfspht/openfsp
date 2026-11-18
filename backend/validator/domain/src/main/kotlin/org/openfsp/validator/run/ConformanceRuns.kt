package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.ScenarioResult
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.report.api.RunSuite
import org.openfsp.validator.problem.ProblemBody
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.report.StoredReport
import org.openfsp.validator.report.SuiteVersion
import org.openfsp.validator.report.spi.ReportFormat
import org.openfsp.validator.report.spi.ReportStore
import org.openfsp.validator.run.api.FollowRuns
import org.openfsp.validator.run.api.PrepareRuns
import org.openfsp.validator.run.api.StartRuns
import org.openfsp.validator.run.api.SweepRuns
import org.openfsp.validator.run.spi.RunAlerts
import org.openfsp.validator.run.spi.RunEventStream
import org.openfsp.validator.run.spi.TargetConnection
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.SuiteSource
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
    private val webhooks: RunWebhooks,
    private val reports: ReportStore,
    private val format: ReportFormat,
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
        return prepared.prepare(kind).also { webhooks.open(it.id) }
    }

    override fun find(id: UUID): PreparedRun? = prepared.find(id)

    override fun webhookUrl(id: UUID): String = webhooks.urlOf(id)

    override fun start(
        id: UUID,
        order: RunOrder,
    ) {
        if (prepared.isStarted(id)) throw ValidatorException(ProblemKind.RUN_STARTED, "Follow ${settings.runsPath}/$id/events until the summary.")
        prepared.starting(id) { run -> launch(run, order) } ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No prepared run $id.")
    }

    override fun follow(
        id: UUID,
        lastEventId: Long?,
        stream: RunEventStream,
    ) {
        sessions.find(id)?.let { return it.subscribe(lastEventId, stream) }
        val stored = reports.find(id) ?: throw ValidatorException(ProblemKind.NOT_FOUND, "No run $id.")
        val replay = sessions.transient(id)
        RunEvents.replay(id, stored.baseUrl, format.read(stored.body)).forEach { (name, payload) -> replay.publish(name, payload) }
        replay.finish(clock.instant())
        replay.subscribe(lastEventId, stream)
    }

    override fun report(id: UUID): String {
        if (prepared.isWaiting(id)) throw ValidatorException(ProblemKind.RUN_NOT_STARTED, "Start it with POST ${settings.runsPath}/$id/start.")
        val session = sessions.find(id)
        if (session != null && !session.finished) throw ValidatorException(ProblemKind.RUN_IN_PROGRESS, "Follow ${settings.runsPath}/$id/events until the summary.")
        reports.find(id)?.let { return it.body }
        session?.failure?.let { throw ValidatorException(it.kind, it.detail) }
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
        val slot = slots.acquire() ?: throw ValidatorException(ProblemKind.BUSY, "This validator runs ${slots.capacity} validations at a time. Try again in a few minutes.")
        val requested =
            try {
                requests.configuration(order, run, webhooks.urlOf(run.id))
            } catch (exception: RuntimeException) {
                slot.close()
                throw exception
            }
        val connection: TargetConnection = requested.connection
        try {
            val configuration = requested.configuration.copy(webhookInbox = webhooks.open(run.id))
            val session = sessions.find(run.id) ?: sessions.open(run.id)
            val suite = source.suite()
            val plan = runner.plan(suite, configuration).mapIndexed { position, planned -> RunEvents.planned(position + 1, planned.scenario, planned.requirement, configuration.needsAssistance(planned.scenario)) }
            session.publish(RunEvents.RUN, RunStartedEvent(run.id, configuration.target.wire, configuration.baseUrl, suiteVersion(), plan.size, plan, suite.catalogue.limits))
            runExecutor.execute {
                slot.use { connection.use { execute(run.id, configuration, connection, session) } }
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
            val report = runner.run(source.suite(), configuration, connection, Progress(session, configuration))
            reports.save(
                StoredReport(
                    id = id,
                    createdAt = clock.instant(),
                    target = configuration.target.wire,
                    baseUrl = configuration.baseUrl,
                    core = report.core.name,
                    suiteVersion = report.suite.version,
                    body = format.write(report),
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
        session.publish(RunEvents.FAILED, ProblemBody.of(kind, detail, "${settings.runsPath}/${session.id}"))
        session.finish(clock.instant(), RunFailure(kind, detail))
    }

    private fun suiteVersion(): SuiteVersion {
        val suite = source.suite()
        return SuiteVersion(suite.catalogue.suite.version, suite.catalogue.suite.protocolVersion)
    }

    private inner class Progress(
        private val session: RunSession,
        private val configuration: Configuration,
    ) : RunListener {
        override fun started(
            index: Int,
            total: Int,
            scenario: Scenario,
            requirement: Requirement,
        ) = session.publish(RunEvents.SCENARIO_STARTED, ScenarioStartedEvent(total, RunEvents.planned(index, scenario, requirement, configuration.needsAssistance(scenario))))

        override fun finished(
            index: Int,
            total: Int,
            result: ScenarioResult,
        ) = session.publish(RunEvents.SCENARIO_FINISHED, ScenarioFinishedEvent(index, total, Reports.entry(result)))
    }
}

package org.openfsp.validator.run

import org.openfsp.validator.TestSuites
import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.engine.spi.stubs.KnownSchemaCheckers
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.report.spi.stubs.InMemoryReportStore
import org.openfsp.validator.report.spi.stubs.RememberedReportFormat
import org.openfsp.validator.run.spi.stubs.CollectingRunEventStream
import org.openfsp.validator.run.spi.stubs.DirectTargetConnections
import org.openfsp.validator.run.spi.stubs.RecordingRunAlerts
import org.openfsp.validator.suite.spi.stubs.FixedSuiteSource
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConformanceRunsTest {
    private val clock = Clock.systemUTC()
    private val settings = TestSettings.run(runsPerAddress = 2, concurrency = 1)
    private val source = FixedSuiteSource(TestSuites.things())
    private val connections = DirectTargetConnections(TestSuites.target())
    private val reports = InMemoryReportStore()
    private val sessions = RunSessions(settings, clock)
    private val prepared = PreparedRuns(settings, sessions, clock)
    private val webhooks = RunWebhooks(settings, sessions, RunStateQuery(prepared, sessions), PlainJsonCodec)
    private val executor = Executors.newSingleThreadExecutor()
    private val slots = RunSlots(settings)
    private val runs =
        ConformanceRuns(
            source,
            settings,
            ConformanceRunner(KnownSchemaCheckers(), PlainJsonCodec),
            RunRequests(settings, source, connections, ServerFixtures(PlainJsonCodec, RequiredMembers)),
            slots,
            RunRateLimiter(settings, clock),
            sessions,
            prepared,
            webhooks,
            reports,
            RememberedReportFormat(),
            RecordingRunAlerts(),
            clock,
            executor,
        )

    @AfterTest
    fun stop() {
        executor.shutdownNow()
    }

    private fun order() = RunOrder(baseUrl = "https://target.test", apiKey = "ofsp_test_key", controlEndpoint = "https://target.test/conformance/control")

    @Test
    fun `a prepared run reaches only its target, stores its report and replays its events`() {
        val run = runs.prepare("server", "203.0.113.7")
        assertEquals("http://validator.test/api/runs/${run.id}/webhooks", runs.webhookUrl(run.id))
        assertEquals(ProblemKind.RUN_NOT_STARTED, assertFailsWith<ValidatorException> { runs.report(run.id) }.kind)

        runs.start(run.id, order())
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals(1, connections.closed.get())
        assertTrue(runs.report(run.id).contains("core="))
        val live = CollectingRunEventStream()
        runs.follow(run.id, null, live)
        assertEquals(listOf(RunEvents.RUN, RunEvents.SCENARIO_STARTED, RunEvents.SCENARIO_FINISHED, RunEvents.SCENARIO_STARTED, RunEvents.SCENARIO_FINISHED, RunEvents.SUMMARY), live.events.map { it.name })
        assertTrue(live.completed)
        assertEquals(ProblemKind.RUN_STARTED, assertFailsWith<ValidatorException> { runs.start(run.id, order()) }.kind)

        sessions.forget(run.id)
        val replayed = CollectingRunEventStream()
        runs.follow(run.id, null, replayed)
        assertEquals(RunEvents.SUMMARY, replayed.events.last().name)
    }

    @Test
    fun `a run request with a live key or an unknown target is refused before any connection`() {
        val run = runs.prepare("server", "203.0.113.7")
        val refused = assertFailsWith<ValidatorException> { runs.start(run.id, order().copy(apiKey = "ofsp_live_key")) }
        assertEquals(ProblemKind.TEST_KEY_REQUIRED, refused.kind)
        assertTrue(connections.opened.isEmpty())
        assertEquals(ProblemKind.INVALID_REQUEST, assertFailsWith<ValidatorException> { runs.prepare("client", "203.0.113.7") }.kind)
    }

    @Test
    fun `a run that finds every slot in use is refused before it reaches the target`() {
        val run = runs.prepare("server", "203.0.113.7")
        val held = slots.acquire()!!

        assertEquals(ProblemKind.BUSY, assertFailsWith<ValidatorException> { runs.start(run.id, RunOrder(baseUrl = "https://target.test")) }.kind)

        assertTrue(connections.opened.isEmpty())
        held.close()
    }

    @Test
    fun `an address is limited to its runs per window`() {
        repeat(2) { runs.prepare("server", "198.51.100.1") }
        assertEquals(ProblemKind.RATE_LIMITED, assertFailsWith<ValidatorException> { runs.prepare("server", "198.51.100.1") }.kind)
    }
}

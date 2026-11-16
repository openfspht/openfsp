package org.openfsp.validator.engine

import org.openfsp.validator.TestSuites
import org.openfsp.validator.engine.spi.stubs.RecordingSimulatorControl
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.TargetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunSuiteTest {
    private val suite = TestSuites.things()
    private val simulator = RecordingSimulatorControl()
    private val runner = ConformanceRunner(simulator)

    private fun configuration(simulatorUrl: String? = "http://simulator.test") = Configuration(TargetKind.GATEWAY, "http://target.test", "ofsp_test_key", simulatorUrl, null, suite.directory)

    @Test
    fun `a run drives the simulator through its port and reports core incomplete while an assisted scenario waits`() {
        val target = TestSuites.target()
        val report = runner.run(suite, configuration(), target)

        assertEquals(listOf("http://simulator.test"), simulator.urls)
        assertEquals(listOf("default mock_openfsp SUCCESS"), simulator.calls)
        assertEquals(listOf(Verdict.ASSISTED, Verdict.PASS), report.results.map { it.verdict })
        assertEquals(LevelVerdict.INCOMPLETE, report.core)
        assertTrue(target.requests.all { it.url.startsWith("http://target.test/") })
        assertTrue(Reports.summary(report).contains("Core: INCOMPLETE"))
    }

    @Test
    fun `a failed expectation fails core and the plan lists the scenarios the target runs`() {
        val report = runner.run(suite, configuration(), TestSuites.target(value = 2))
        assertEquals(LevelVerdict.FAIL, report.core)
        assertEquals(listOf("things/assisted", "things/pass"), runner.plan(suite, configuration()).map { it.scenario.id }.sorted())
    }

    @Test
    fun `without a simulator URL a scenario that drives it is assisted and no control is opened`() {
        val report = runner.run(suite, configuration(simulatorUrl = null), TestSuites.target())
        assertEquals(Verdict.ASSISTED, report.results.single { it.scenario == "things/pass" }.verdict)
        assertTrue(simulator.urls.isEmpty())
    }

    @Test
    fun `a target on another major protocol version is refused`() {
        val target = ScriptedTransport(mapOf("GET /.well-known/openfsp-configuration" to (200 to """{"protocol_version":"1.0.0"}""")))
        assertFailsWith<VersionRefusedException> { runner.run(suite, configuration(), target) }
    }
}

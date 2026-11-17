package org.openfsp.validator.engine

import org.openfsp.validator.TestSuites
import org.openfsp.validator.engine.spi.stubs.RecordingSimulatorControl
import org.openfsp.validator.engine.spi.stubs.KnownSchemaCheckers
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.TargetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunSuiteTest {
    private val suite = TestSuites.things()
    private val simulator = RecordingSimulatorControl()
    private val runner = ConformanceRunner(simulator, KnownSchemaCheckers(), PlainJsonCodec)

    private fun configuration(simulatorUrl: String? = "http://simulator.test") = Configuration(TargetKind.SERVER, "http://target.test", "ofsp_test_key", simulatorUrl)

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
    fun `the report names the suite by version and shows the published simulator URL in place of the one it drove`() {
        val driven = runner.run(suite, configuration().copy(publishedSimulatorUrl = "https://simulator.example"), TestSuites.target())
        assertEquals("openfsp conformance suite 0.0.1", driven.configuration["conformance"])
        assertEquals("https://simulator.example", driven.configuration["simulator_url"])
        val alone = runner.run(suite, configuration(simulatorUrl = null).copy(publishedSimulatorUrl = "https://simulator.example"), TestSuites.target())
        assertEquals(null, alone.configuration["simulator_url"])
        assertEquals(driven.configuration.keys, alone.configuration.keys)
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
    fun `a template that resolves to an invalid pointer makes the scenario inconclusive instead of stopping the run`() {
        val pass = suite.scenarios.single { it.id == "things/pass" }
        val broken = pass.copy(steps = pass.steps.map { step -> step.expect?.let { step.copy(expect = it.copy(equals = mapOf("{{reference}}" to JsonNumber.of(1)))) } ?: step })
        val report = runner.run(suite.copy(scenarios = listOf(broken)), configuration(), TestSuites.target())
        val result = report.results.single()
        assertEquals(Verdict.INCONCLUSIVE, result.verdict)
        assertTrue(result.reason.orEmpty().contains("must start with /"), result.reason)
    }

    @Test
    fun `a target on another major protocol version is refused`() {
        val target = ScriptedTransport(mapOf("GET /.well-known/openfsp-configuration" to (200 to """{"protocol_version":"1.0.0"}""")))
        assertFailsWith<VersionRefusedException> { runner.run(suite, configuration(), target) }
    }
}

package org.openfsp.validator.report

import org.openfsp.validator.TestSuites
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.LevelVerdict
import org.openfsp.validator.engine.Verdict
import org.openfsp.validator.engine.VersionRefusedException
import org.openfsp.validator.engine.spi.stubs.KnownSchemaCheckers
import org.openfsp.validator.engine.spi.stubs.ScriptedTransport
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.spi.stubs.PlainJsonCodec
import org.openfsp.validator.suite.TargetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunSuiteTest {
    private val suite = TestSuites.things()
    private val runner = ConformanceRunner(KnownSchemaCheckers(), PlainJsonCodec)

    private fun configuration(controlEndpoint: String? = "http://target.test${TestSuites.CONTROL}") = Configuration(TargetKind.SERVER, "http://target.test", "ofsp_test_key", controlEndpoint)

    @Test
    fun `a run chooses its outcomes on the control interface with the key of the run and reports core incomplete while an assisted scenario waits`() {
        val target = TestSuites.target()
        val report = runner.run(suite, configuration(), target)

        val declared = target.requests.single { it.method == "PUT" }
        assertEquals("http://target.test${TestSuites.CONTROL}/references/order-1/scenario", declared.url)
        assertEquals("Bearer ofsp_test_key", declared.headers["Authorization"])
        assertEquals("""{"scenario":"SUCCESS"}""", String(declared.body!!))
        assertEquals(listOf(Verdict.ASSISTED, Verdict.PASS), report.results.map { it.verdict })
        assertEquals(LevelVerdict.INCOMPLETE, report.core)
        assertTrue(target.requests.all { it.url.startsWith("http://target.test/") })
        assertTrue(Reports.summary(report).contains("Core: INCOMPLETE"))
    }

    @Test
    fun `a scenario whose capability the target lacks is not applicable before it can be assisted`() {
        val gated = suite.copy(scenarios = suite.scenarios.map { if (it.id == "things/assisted") it.copy(withCapabilities = listOf("payments.statement")) else it })
        val result = runner.run(gated, configuration(), TestSuites.target()).results.single { it.scenario == "things/assisted" }
        assertEquals(Verdict.NOT_APPLICABLE, result.verdict)
        assertEquals(false, result.assisted)
    }

    @Test
    fun `a scenario run with the input it needs is not reported as assisted`() {
        val provided = runner.run(suite, configuration().copy(inputs = mapOf("restricted-key" to "ofsp_test_restricted")), TestSuites.target()).results.single { it.scenario == "things/assisted" }
        assertEquals(false, provided.assisted)
        assertEquals(null, provided.action)
    }

    @Test
    fun `the report names the suite by version and states that the suite chose the outcomes, if it did`() {
        val driven = runner.run(suite, configuration(), TestSuites.target())
        assertEquals("openfsp conformance suite 0.0.1", driven.configuration["conformance"])
        assertEquals("suite", driven.configuration["outcomes"])
        val alone = runner.run(suite, configuration(controlEndpoint = null), TestSuites.target())
        assertEquals(null, alone.configuration["outcomes"])
        assertEquals(driven.configuration.keys, alone.configuration.keys)
    }

    @Test
    fun `a failed expectation fails core and the plan lists the scenarios the target runs`() {
        val report = runner.run(suite, configuration(), TestSuites.target(value = 2))
        assertEquals(LevelVerdict.FAIL, report.core)
        assertEquals(listOf("things/assisted", "things/pass"), runner.plan(suite, configuration()).map { it.scenario.id }.sorted())
    }

    @Test
    fun `without a control interface a scenario that chooses an outcome fails and says so`() {
        val target = TestSuites.target()
        val result = runner.run(suite, configuration(controlEndpoint = null), target).results.single { it.scenario == "things/pass" }
        assertEquals(Verdict.FAIL, result.verdict)
        assertTrue(result.reason.orEmpty().contains("offers no control interface"), result.reason)
        assertTrue(target.requests.none { it.method == "PUT" })
    }

    @Test
    fun `a scenario the implementation refuses fails, and another refusal of the control interface is inconclusive`() {
        val unavailable = runner.run(suite, configuration(), TestSuites.target(declared = 422)).results.single { it.scenario == "things/pass" }
        assertEquals(Verdict.FAIL, unavailable.verdict)
        assertTrue(unavailable.reason.orEmpty().contains("refused the scenario SUCCESS"), unavailable.reason)
        val refused = runner.run(suite, configuration(), TestSuites.target(declared = 401)).results.single { it.scenario == "things/pass" }
        assertEquals(Verdict.INCONCLUSIVE, refused.verdict)
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

package org.openfsp.validator.engine

import org.openfsp.validator.TestSuites
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.suite.Expectation
import org.openfsp.validator.suite.Suite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueCheckTest {
    private val catalogue = CatalogueCheck(Checks())

    private fun withExpectation(expectation: Expectation): Suite {
        val suite = TestSuites.things()
        val pass = suite.scenarios.single { it.id == "things/pass" }
        val broken = pass.copy(steps = pass.steps.map { step -> step.expect?.let { step.copy(expect = expectation) } ?: step })
        return suite.copy(scenarios = suite.scenarios.map { if (it.id == pass.id) broken else it })
    }

    @Test
    fun `the test suite is consistent`() {
        assertEquals(emptyList(), catalogue.problems(TestSuites.things()))
    }

    @Test
    fun `a pointer that does not start with a slash and a pattern that does not compile are reported`() {
        val problems = catalogue.problems(withExpectation(Expectation(status = 200, equals = mapOf("value" to JsonNumber.of(1)), matches = mapOf("/value" to "["))))
        assertTrue(problems.any { "names value, expected a JSON Pointer" in it }, problems.toString())
        assertTrue(problems.any { "matches [, which is not a regular expression" in it }, problems.toString())
    }

    @Test
    fun `pointers and patterns built from templates are left to the run`() {
        assertEquals(emptyList(), catalogue.problems(withExpectation(Expectation(status = 200, equals = mapOf("{{member}}" to JsonNumber.of(1)), matches = mapOf("/value" to "{{pattern}}")))))
    }
}

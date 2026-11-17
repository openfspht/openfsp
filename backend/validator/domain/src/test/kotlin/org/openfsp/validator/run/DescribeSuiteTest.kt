package org.openfsp.validator.run

import org.openfsp.validator.TestSuites
import org.openfsp.validator.run.api.AssistedScenario
import org.openfsp.validator.suite.spi.stubs.FixedSuiteSource
import kotlin.test.Test
import kotlin.test.assertEquals

class DescribeSuiteTest {
    @Test
    fun `the description lists the offered targets, the assisted inputs and the run capacity`() {
        val settings = TestSettings.run(concurrency = 3)
        val description = SuiteCatalogue(FixedSuiteSource(TestSuites.things()), settings, RunSlots(settings)).describe()

        assertEquals("0.0.1", description.version)
        assertEquals(listOf("server"), description.targets)
        assertEquals("restricted-key", description.restrictedKeyInput)
        assertEquals(3, description.concurrency)
        assertEquals(listOf(AssistedScenario("things/assisted", "T-2", "provide a restricted key", listOf("restricted-key"))), description.assisted)
    }
}

package org.openfsp.validator.suites

import org.openfsp.protocol.ProtocolVersion
import org.openfsp.validator.engine.CatalogueCheck
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueTest {
    private val suite = SuiteFiles.load(Path.of(System.getProperty("openfsp.conformance")))

    @Test
    fun `every scenario cites a known requirement, every requirement has a scenario and every schema exists`() {
        assertEquals(emptyList(), CatalogueCheck.problems(suite))
    }

    @Test
    fun `every schema compiles under draft 2020-12`() {
        val schemas = NetworkntSchemaCheckers().of(suite.schemas)
        val sample = JsonMapper.builder().build().readTree("""{"data":[{"amount":{"amount":1,"currency":"HTG"}}],"providers":[{}]}""")
        suite.schemas.keys.forEach { schemas.validate(it, sample) }
        assertTrue("payment" in suite.schemas)
    }

    @Test
    fun `the suite tests the protocol version of the protocol module`() {
        assertEquals(ProtocolVersion.CURRENT, suite.catalogue.suite.protocolVersion)
    }
}

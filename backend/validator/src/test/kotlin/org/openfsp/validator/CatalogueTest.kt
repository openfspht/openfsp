package org.openfsp.validator

import org.openfsp.protocol.ProtocolVersion
import org.openfsp.validator.engine.Schemas
import org.openfsp.validator.suite.CatalogueCheck
import org.openfsp.validator.suite.SuiteLoader
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueTest {
    private val suite = SuiteLoader.load(Path.of(System.getProperty("openfsp.conformance")))

    @Test
    fun `every scenario cites a known requirement, every requirement has a scenario and every schema exists`() {
        assertEquals(emptyList(), CatalogueCheck.problems(suite))
    }

    @Test
    fun `every schema compiles under draft 2020-12`() {
        val schemas = Schemas(suite.schemas)
        val sample = JsonMapper.builder().build().readTree("""{"data":[{"amount":{"amount":1,"currency":"HTG"}}],"providers":[{}]}""")
        schemas.names.forEach { schemas.validate(it, sample) }
        assertTrue("payment" in schemas.names)
    }

    @Test
    fun `the suite tests the protocol version of the protocol module`() {
        assertEquals(ProtocolVersion.CURRENT, suite.catalogue.suite.protocolVersion)
    }
}

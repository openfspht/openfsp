package org.openfsp.validator.suites

import org.openfsp.validator.engine.CatalogueCheck
import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Durations
import org.openfsp.validator.engine.JwkKeys
import org.openfsp.validator.engine.Pointers
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledConformanceTest {
    @Test
    fun `the bundled suite loads and leaves no extracted copy behind`() {
        val workspace = Files.createTempDirectory("workspace")
        val suite = BundledConformance(CatalogueCheck(Checks(Pointers(), JwkKeys()), Durations())).load(workspace)
        assertTrue(suite.scenarios.isNotEmpty())
        assertEquals(emptyList(), workspace.listDirectoryEntries())
    }
}

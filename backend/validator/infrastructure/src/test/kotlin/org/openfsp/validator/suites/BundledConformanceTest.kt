package org.openfsp.validator.suites

import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledConformanceTest {
    @Test
    fun `the bundled suite loads and leaves no extracted copy behind`() {
        val workspace = Files.createTempDirectory("workspace")
        val suite = BundledConformance.load(workspace)
        assertTrue(suite.scenarios.isNotEmpty())
        assertEquals(emptyList(), workspace.listDirectoryEntries())
    }
}

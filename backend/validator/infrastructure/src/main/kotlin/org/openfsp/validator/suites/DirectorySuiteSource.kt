package org.openfsp.validator.suites

import org.openfsp.validator.engine.CatalogueCheck
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.spi.SuiteLoadException
import org.openfsp.validator.suite.spi.SuiteSource
import java.nio.file.Path

class DirectorySuiteSource(
    directory: Path,
) : SuiteSource {
    private val suite: Suite =
        SuiteFiles.load(directory).also { suite ->
            val problems = CatalogueCheck.problems(suite)
            if (problems.isNotEmpty()) throw SuiteLoadException("the conformance catalogue in $directory is inconsistent", problems)
        }

    override fun suite(): Suite = suite
}

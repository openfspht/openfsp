package org.openfsp.validator.engine.api

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.report.Report
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario
import org.openfsp.validator.suite.Suite

data class PlannedRun(
    val scenario: Scenario,
    val requirement: Requirement,
)

interface RunSuite {
    fun plan(
        suite: Suite,
        configuration: Configuration,
    ): List<PlannedRun>

    fun run(
        suite: Suite,
        configuration: Configuration,
        target: Transport,
        listener: RunListener = RunListener.SILENT,
    ): Report
}

package org.openfsp.validator.engine

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.api.PlannedRun
import org.openfsp.validator.engine.api.RunSuite
import org.openfsp.validator.engine.spi.SimulatorControls
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.Reports
import org.openfsp.validator.suite.Suite

@DomainService
class ConformanceRunner(
    private val simulators: SimulatorControls,
) : RunSuite {
    override fun plan(
        suite: Suite,
        configuration: Configuration,
    ): List<PlannedRun> {
        val engine = Engine(suite, configuration, RunListener.SILENT, { error("planning sends no request") }, null)
        return engine.plan().map { PlannedRun(it, engine.requirementOf(it)) }
    }

    override fun run(
        suite: Suite,
        configuration: Configuration,
        target: Transport,
        listener: RunListener,
    ): Report = Reports.of(suite, configuration, engine(suite, configuration, target, listener).run())

    fun engine(
        suite: Suite,
        configuration: Configuration,
        target: Transport,
        listener: RunListener,
    ): Engine = Engine(suite, configuration, listener, target, configuration.simulatorUrl?.let { simulators.at(it, configuration.timeout) })
}

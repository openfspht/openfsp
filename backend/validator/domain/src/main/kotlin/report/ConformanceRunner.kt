package org.openfsp.validator.report

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Engine
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.spi.Pause
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.report.api.PlannedRun
import org.openfsp.validator.report.api.RunSuite
import org.openfsp.validator.suite.Suite
import java.time.Clock
import java.util.concurrent.ExecutorService

@DomainService
class ConformanceRunner(
    private val schemas: SchemaCheckers,
    private val json: JsonCodec,
    private val clock: Clock,
    private val pause: Pause,
    private val executor: ExecutorService,
    private val checks: Checks,
) : RunSuite {
    override fun plan(
        suite: Suite,
        configuration: Configuration,
    ): List<PlannedRun> {
        val engine = Engine(suite, configuration, RunListener.SILENT, { error("planning sends no request") }, { schemas.of(suite.schemas) }, json, clock, pause, executor, checks)
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
    ): Engine = Engine(suite, configuration, listener, target, { schemas.of(suite.schemas) }, json, clock, pause, executor, checks)
}

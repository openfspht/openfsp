package org.openfsp.validator

import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Engine
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.engine.spi.Pause
import org.openfsp.validator.report.ConformanceRunner
import org.openfsp.validator.runs.http.JdkTransport
import org.openfsp.validator.shared.json.JacksonJsonCodec
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suites.NetworkntSchemaCheckers
import java.time.Clock
import java.util.concurrent.Executors

fun engine(
    suite: Suite,
    configuration: Configuration,
    listener: RunListener = RunListener.SILENT,
): Engine = ConformanceRunner(NetworkntSchemaCheckers(), JacksonJsonCodec(), Clock.systemUTC(), Pause(Thread::sleep), Executors.newVirtualThreadPerTaskExecutor(), Checks()).engine(suite, configuration, JdkTransport(configuration.timeout), listener)

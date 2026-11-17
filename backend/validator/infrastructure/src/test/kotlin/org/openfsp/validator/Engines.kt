package org.openfsp.validator

import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.Engine
import org.openfsp.validator.engine.RunListener
import org.openfsp.validator.http.JdkTransport
import org.openfsp.validator.shared.configuration.EngineAdapters
import org.openfsp.validator.suite.Suite

fun engine(
    suite: Suite,
    configuration: Configuration,
    listener: RunListener = RunListener.SILENT,
): Engine = EngineAdapters.runner().engine(suite, configuration, JdkTransport(configuration.timeout), listener)

package org.openfsp.validator.suites

import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.spi.SuiteSource
import org.springframework.stereotype.Component

@Component
class ConformanceSuite(
    properties: ValidatorProperties,
) : SuiteSource {
    private val suite: Suite = properties.conformance?.let { DirectorySuiteSource(it).suite() } ?: BundledConformance.load()

    override fun suite(): Suite = suite
}

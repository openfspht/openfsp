package org.openfsp.validator.suites

import org.openfsp.validator.engine.CatalogueCheck
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.spi.SuiteSource
import org.springframework.stereotype.Component

@Component
class ConformanceSuite(
    properties: ValidatorProperties,
    bundled: BundledConformance,
    catalogue: CatalogueCheck,
) : SuiteSource {
    private val suite: Suite = properties.conformance?.let { DirectorySuiteSource(it, catalogue).suite() } ?: bundled.load()

    override fun suite(): Suite = suite
}

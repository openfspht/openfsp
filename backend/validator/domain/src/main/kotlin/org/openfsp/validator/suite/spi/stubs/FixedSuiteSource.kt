package org.openfsp.validator.suite.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.spi.SuiteSource

@Stub
class FixedSuiteSource(
    private val suite: Suite,
) : SuiteSource {
    override fun suite(): Suite = suite
}

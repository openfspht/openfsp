package org.openfsp.validator.suite.spi

import org.openfsp.validator.suite.Suite

fun interface SuiteSource {
    fun suite(): Suite
}

package org.openfsp.validator.suite.spi

import org.openfsp.validator.suite.Suite

class SuiteLoadException(
    message: String,
    val problems: List<String> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)

fun interface SuiteSource {
    fun suite(): Suite
}

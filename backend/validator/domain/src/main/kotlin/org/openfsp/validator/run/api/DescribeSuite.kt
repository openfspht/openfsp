package org.openfsp.validator.run.api

import org.openfsp.validator.suite.Limit

data class AssistedScenario(
    val scenario: String,
    val requirementId: String,
    val action: String,
    val inputs: List<String>,
)

data class SuiteDescription(
    val version: String,
    val protocolVersion: String,
    val targets: List<String>,
    val restrictedKeyInput: String,
    val environment: String,
    val keyPrefix: String,
    val concurrency: Int,
    val assisted: List<AssistedScenario>,
    val limits: List<Limit>,
)

interface DescribeSuite {
    fun describe(): SuiteDescription
}

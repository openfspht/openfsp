package org.openfsp.validator.suites

import org.openfsp.validator.suite.Limit
import org.openfsp.validator.suite.SuiteLocale
import org.openfsp.validator.run.api.DescribeSuite
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AssistedScenarioResource(
    val scenario: String,
    val requirementId: String,
    val action: String,
    val inputs: List<String>,
)

data class SuiteResource(
    val version: String,
    val protocolVersion: String,
    val specUrl: String,
    val targets: List<String>,
    val restrictedKeyInput: String,
    val keyPrefix: String,
    val concurrency: Int,
    val retentionDays: Long,
    val assisted: List<AssistedScenarioResource>,
    val limits: List<Limit>,
    val locales: Map<String, SuiteLocale>,
)

@RestController
@RequestMapping("/api")
class SuiteController(
    private val suites: DescribeSuite,
    private val properties: ValidatorProperties,
) {
    @GetMapping("/suite")
    fun suite(): SuiteResource {
        val suite = suites.describe()
        return SuiteResource(
            version = suite.version,
            protocolVersion = suite.protocolVersion,
            specUrl = properties.specUrl,
            targets = suite.targets,
            restrictedKeyInput = suite.restrictedKeyInput,
            keyPrefix = suite.keyPrefix,
            concurrency = suite.concurrency,
            retentionDays = properties.retention.toDays(),
            assisted = suite.assisted.map { AssistedScenarioResource(it.scenario, it.requirementId, it.action, it.inputs) },
            limits = suite.limits,
            locales = suite.locales,
        )
    }
}

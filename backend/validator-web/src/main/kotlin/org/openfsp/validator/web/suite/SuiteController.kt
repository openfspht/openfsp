package org.openfsp.validator.web.suite

import org.openfsp.validator.suite.Limit
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.runs.RunRequests
import org.openfsp.validator.web.runs.RunSlots
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
    val environment: String,
    val keyPrefix: String,
    val concurrency: Int,
    val assisted: List<AssistedScenarioResource>,
    val limits: List<Limit>,
)

@RestController
@RequestMapping("/api")
class SuiteController(
    private val conformance: ConformanceSuite,
    private val properties: ValidatorProperties,
    private val slots: RunSlots,
) {
    @GetMapping("/suite")
    fun suite(): SuiteResource {
        val suite = conformance.suite
        return SuiteResource(
            version = suite.catalogue.suite.version,
            protocolVersion = suite.catalogue.suite.protocolVersion,
            specUrl = properties.specUrl,
            targets = RunRequests.OFFERED_TARGETS.map { it.wire },
            restrictedKeyInput = RunRequests.RESTRICTED_KEY_INPUT,
            environment = properties.simulator.environment,
            keyPrefix = properties.simulator.keyPrefix,
            concurrency = slots.capacity,
            assisted =
                suite.scenarios.mapNotNull { scenario ->
                    scenario.assisted?.let { AssistedScenarioResource(scenario.id, scenario.requirement, it.action, it.inputs) }
                },
            limits = suite.catalogue.limits,
        )
    }
}

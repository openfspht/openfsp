package org.openfsp.validator.web.suite

import org.openfsp.validator.suite.Limit
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.runs.RunRequests
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
    val assisted: List<AssistedScenarioResource>,
    val limits: List<Limit>,
)

data class SimulatedOperatorResource(
    val id: String,
    val dialect: String,
    val displayName: String?,
    val baseUrl: String,
    val secondaryUrl: String?,
    val credentials: Map<String, String>,
)

data class SimulatorConfigResource(
    val simulatorUrl: String,
    val environment: String,
    val keyPrefix: String,
    val derivationKey: String?,
    val operators: List<SimulatedOperatorResource>,
)

@RestController
@RequestMapping("/api")
class SuiteController(
    private val conformance: ConformanceSuite,
    private val properties: ValidatorProperties,
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
            assisted =
                suite.scenarios.mapNotNull { scenario ->
                    scenario.assisted?.let { AssistedScenarioResource(scenario.id, scenario.requirement, it.action, it.inputs) }
                },
            limits = suite.catalogue.limits,
        )
    }

    @GetMapping("/simulator-config")
    fun simulatorConfig(): SimulatorConfigResource {
        val simulator = properties.simulator
        val base = simulator.publicUrl.trimEnd('/')
        return SimulatorConfigResource(
            simulatorUrl = base,
            environment = simulator.environment,
            keyPrefix = simulator.keyPrefix,
            derivationKey = simulator.derivationKey,
            operators =
                simulator.operators.map { (id, operator) ->
                    SimulatedOperatorResource(id, operator.dialect, operator.displayName, base + operator.basePath, operator.secondaryPath?.let { base + it }, operator.credentials)
                },
        )
    }
}

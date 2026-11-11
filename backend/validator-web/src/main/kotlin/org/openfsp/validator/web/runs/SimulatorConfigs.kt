package org.openfsp.validator.web.runs

import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.stereotype.Component
import java.time.Instant

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
    val space: String,
    val startBefore: Instant,
    val environment: String,
    val keyPrefix: String,
    val derivationKey: String?,
    val operators: List<SimulatedOperatorResource>,
)

@Component
class SimulatorConfigs(
    private val properties: ValidatorProperties,
) {
    fun of(run: PreparedRun): SimulatorConfigResource {
        val simulator = properties.simulator
        val base = run.space.publicUrl
        return SimulatorConfigResource(
            simulatorUrl = base,
            space = run.space.id,
            startBefore = run.startBefore,
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

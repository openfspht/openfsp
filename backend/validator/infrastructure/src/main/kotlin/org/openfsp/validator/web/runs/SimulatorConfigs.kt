package org.openfsp.validator.web.runs

import org.openfsp.validator.run.PreparedRun
import org.openfsp.validator.run.api.PrepareRuns
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.springframework.stereotype.Component
import java.time.Instant

data class SimulatedProviderResource(
    val id: String,
    val displayName: String?,
    val baseUrl: String,
    val credentials: Map<String, String>,
)

data class SimulatorConfigResource(
    val simulatorUrl: String,
    val space: String,
    val startBefore: Instant,
    val environment: String,
    val keyPrefix: String,
    val webhookUrl: String,
    val providers: List<SimulatedProviderResource>,
)

@Component
class SimulatorConfigs(
    private val properties: ValidatorProperties,
    private val runs: PrepareRuns,
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
            webhookUrl = runs.webhookUrl(run.id),
            providers =
                simulator.providers.map { (id, provider) ->
                    SimulatedProviderResource(id, provider.displayName, base + provider.basePath, provider.credentials)
                },
        )
    }
}

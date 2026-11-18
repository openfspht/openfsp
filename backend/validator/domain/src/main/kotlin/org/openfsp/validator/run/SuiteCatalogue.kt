package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.run.api.AssistedScenario
import org.openfsp.validator.run.api.DescribeSuite
import org.openfsp.validator.run.api.SuiteDescription
import org.openfsp.validator.suite.spi.SuiteSource

@DomainService
class SuiteCatalogue(
    private val source: SuiteSource,
    private val settings: RunSettings,
    private val slots: RunSlots,
) : DescribeSuite {
    override fun describe(): SuiteDescription {
        val suite = source.suite()
        return SuiteDescription(
            version = suite.catalogue.suite.version,
            protocolVersion = suite.catalogue.suite.protocolVersion,
            targets = RunRequests.OFFERED_TARGETS.map { it.wire },
            restrictedKeyInput = RunRequests.RESTRICTED_KEY_INPUT,
            environment = settings.environment,
            keyPrefix = settings.keyPrefix,
            concurrency = slots.capacity,
            assisted =
                suite.scenarios.mapNotNull { scenario ->
                    scenario.assisted?.let { AssistedScenario(scenario.id, scenario.requirement, it.action, it.inputs) }
                },
            limits = suite.catalogue.limits,
        )
    }
}

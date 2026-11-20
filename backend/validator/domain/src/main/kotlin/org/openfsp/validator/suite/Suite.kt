package org.openfsp.validator.suite

import org.openfsp.validator.json.Json

data class Suite(
    val catalogue: Catalogue,
    val scenarios: List<Scenario>,
    val schemas: Map<String, Json>,
    val locales: Map<String, SuiteLocale> = emptyMap(),
) {
    private val byId = catalogue.requirements.associateBy { it.id }

    fun requirement(id: String): Requirement? = byId[id]

    fun requirement(
        id: String,
        target: TargetKind,
    ): Requirement? = byId[id]?.on(target)

    fun applies(
        scenario: Scenario,
        target: TargetKind,
    ): Boolean = requirement(scenario.requirement)?.targets?.contains(target) == true && scenario.runsOn(target)
}

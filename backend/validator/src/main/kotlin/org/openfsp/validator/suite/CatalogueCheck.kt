package org.openfsp.validator.suite

import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Durations

object CatalogueCheck {
    private val specReference = Regex("^[a-z-]+\\.md#[0-9]+(\\.[0-9]+)*$")
    private val profileName = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")
    private const val SECURITY_LIMIT = "conformite.md#5.6"

    fun problems(suite: Suite): List<String> {
        val problems = mutableListOf<String>()
        val catalogue = suite.catalogue
        catalogue.requirements.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "requirement $it is declared more than once" }
        catalogue.requirements.forEach { requirement ->
            if (!specReference.matches(requirement.requirement)) problems += "requirement ${requirement.id} cites ${requirement.requirement}, expected file.md#rule"
            if (!profileName.matches(requirement.level)) problems += "requirement ${requirement.id} has level ${requirement.level}, expected core or a capability name"
            if (requirement.title.isBlank()) problems += "requirement ${requirement.id} has no title"
        }
        if (catalogue.limits.none { it.reference == SECURITY_LIMIT }) problems += "the limits do not state $SECURITY_LIMIT"
        runCatching { Durations.parse(catalogue.suite.journalSettle) }.onFailure { problems += "suite journal-settle: ${it.message}" }

        val covered = suite.scenarios.map { it.requirement }.toSet()
        catalogue.requirements.filter { it.id !in covered }.forEach { problems += "requirement ${it.id} has no scenario" }
        suite.scenarios.forEach { scenario ->
            val where = "scenario ${scenario.id}"
            if (suite.requirement(scenario.requirement) == null) problems += "$where references unknown requirement ${scenario.requirement}"
            if (scenario.title.isBlank()) problems += "$where has no title"
            if (scenario.steps.isEmpty()) problems += "$where has no step"
            scenario.steps.forEachIndexed { index, step ->
                val at = "$where step ${step.name ?: index + 1}"
                val expectations = listOfNotNull(step.expect, step.await?.until)
                expectations.mapNotNull { it.schema }.filter { it !in suite.schemas }.forEach { problems += "$at references missing schema $it" }
                step.check?.let { if (Checks.find(it.name) == null) problems += "$at calls unknown check ${it.name}" }
                step.await?.let { await ->
                    listOf(await.within, await.every).forEach { text -> runCatching { Durations.parse(text) }.onFailure { problems += "$at: ${it.message}" } }
                }
                if (listOfNotNull(step.request, step.await, step.simulator, step.journal, step.check).isEmpty()) problems += "$at does nothing"
                if (step.expect != null && step.request == null && step.await == null) problems += "$at expects without a request"
            }
        }
        return problems
    }
}

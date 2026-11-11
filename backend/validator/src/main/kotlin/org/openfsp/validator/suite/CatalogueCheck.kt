package org.openfsp.validator.suite

import org.openfsp.validator.engine.Checks
import org.openfsp.validator.engine.Durations

object CatalogueCheck {
    private val specReference = Regex("^[a-z-]+\\.md#[0-9]+(\\.[0-9]+)*$")
    private val profileName = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")
    private const val SECURITY_LIMIT = "conformite.md#5.6"
    private val REPLY_STATUSES = 100..599

    fun problems(suite: Suite): List<String> {
        val problems = mutableListOf<String>()
        val catalogue = suite.catalogue
        catalogue.requirements.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "requirement $it is declared more than once" }
        catalogue.requirements.forEach { requirement ->
            if (!specReference.matches(requirement.requirement)) problems += "requirement ${requirement.id} cites ${requirement.requirement}, expected file.md#rule"
            if (!profileName.matches(requirement.level)) problems += "requirement ${requirement.id} has level ${requirement.level}, expected core or a capability name"
            if (requirement.title.isBlank()) problems += "requirement ${requirement.id} has no title"
            if (requirement.finding?.isBlank() == true) problems += "requirement ${requirement.id} has a blank finding"
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
                problems += stepProblems(suite, step, at)
                step.parallel.forEachIndexed { position, inner ->
                    val within = "$at parallel ${inner.name ?: position + 1}"
                    problems += stepProblems(suite, inner, within)
                    if (inner.request == null || listOfNotNull(inner.await, inner.simulator, inner.journal, inner.check, inner.webhook, inner.receiver).isNotEmpty() || inner.parallel.isNotEmpty()) problems += "$within must be a request"
                }
            }
        }
        return problems
    }

    private fun stepProblems(
        suite: Suite,
        step: Step,
        at: String,
    ): List<String> {
        val problems = mutableListOf<String>()
        val expectations = listOfNotNull(step.expect, step.await?.until).flatMap(::flatten)
        expectations.mapNotNull { it.schema }.filter { it !in suite.schemas }.forEach { problems += "$at references missing schema $it" }
        step.check?.let { if (Checks.find(it.name) == null) problems += "$at calls unknown check ${it.name}" }
        val webhook = step.webhook
        val durations =
            listOfNotNull(
                step.await?.within, step.await?.every, step.journal?.within, step.journal?.during, step.journal?.every, step.simulator?.settled?.within, step.simulator?.settled?.every,
                webhook?.within, webhook?.during, webhook?.every, webhook?.notSooner, step.receiver?.reply?.delay,
            )
        durations.forEach { text -> runCatching { Durations.parse(text) }.onFailure { problems += "$at: ${it.message}" } }
        if (listOfNotNull(step.request, step.await, step.simulator, step.journal, step.check, step.webhook, step.receiver).isEmpty() && step.parallel.isEmpty()) problems += "$at does nothing"
        if (step.expect != null && listOfNotNull(step.request, step.await, step.webhook, step.journal).isEmpty()) problems += "$at expects without a request, a delivery or a journal record"
        if (listOfNotNull(step.request, step.await, step.webhook).size > 1) problems += "$at combines a request, an await and a delivery"
        webhook?.let {
            if (it.within != null && it.during != null) problems += "$at waits within and holds during at once"
            if (it.pick < 1) problems += "$at picks delivery ${it.pick}, expected 1 or more"
            if ((it.notSooner == null) != (it.after == null)) problems += "$at needs both after and not-sooner"
        }
        step.receiver?.reply?.let { if (it.status !in REPLY_STATUSES) problems += "$at replies ${it.status}, expected an HTTP status" }
        step.journal?.let { journal ->
            if (journal.mark != null && (journal.count != null || journal.atLeast != null || journal.atMost != null || journal.capture.isNotEmpty() || journal.order.isNotEmpty())) problems += "$at marks the journal and asserts at once"
            if (journal.order.size == 1) problems += "$at orders a single selector"
            if (journal.within != null && journal.during != null) problems += "$at waits within and holds during at once"
        }
        return problems
    }

    private fun flatten(expectation: Expectation): List<Expectation> = listOf(expectation) + expectation.anyOf.flatMap(::flatten)
}

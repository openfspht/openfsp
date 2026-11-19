package org.openfsp.validator.engine

import org.openfsp.ddd.DomainService
import org.openfsp.validator.suite.Expectation
import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Step
import org.openfsp.validator.suite.Suite
import org.openfsp.validator.suite.TargetKind
import java.util.regex.PatternSyntaxException

@DomainService
class CatalogueCheck(
    private val checks: Checks,
    private val durations: Durations,
) {
    fun problems(suite: Suite): List<String> {
        val problems = mutableListOf<String>()
        val catalogue = suite.catalogue
        catalogue.requirements.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "requirement $it is declared more than once" }
        catalogue.requirements.forEach { requirement ->
            if (!SPEC_REFERENCE.matches(requirement.requirement)) problems += "requirement ${requirement.id} cites ${requirement.requirement}, expected chapter#rule"
            if (!PROFILE_NAME.matches(requirement.level)) problems += "requirement ${requirement.id} has level ${requirement.level}, expected core or a capability name"
            if (requirement.title.isBlank()) problems += "requirement ${requirement.id} has no title"
            if (requirement.finding?.isBlank() == true) problems += "requirement ${requirement.id} has a blank finding"
            if (requirement.targets.isEmpty()) problems += "requirement ${requirement.id} names no target"
            if (requirement.targets.size != requirement.targets.toSet().size) problems += "requirement ${requirement.id} names a target twice"
            requirement.levels.forEach { (target, level) ->
                val kind = runCatching { TargetKind.of(target) }.getOrNull()
                if (kind == null || kind !in requirement.targets) problems += "requirement ${requirement.id} sets a level for $target, which is not one of its targets"
                if (!PROFILE_NAME.matches(level)) problems += "requirement ${requirement.id} has level $level for $target, expected core or a capability name"
            }
        }
        if (catalogue.limits.none { it.reference == SECURITY_LIMIT }) problems += "the limits do not state $SECURITY_LIMIT"

        catalogue.requirements.forEach { requirement ->
            requirement.targets.filter { target -> suite.scenarios.none { it.requirement == requirement.id && suite.applies(it, target) } }.forEach { target ->
                problems += "requirement ${requirement.id} has no scenario${if (requirement.targets.size > 1) " for target ${target.wire}" else ""}"
            }
        }
        suite.scenarios.forEach { scenario ->
            val where = "scenario ${scenario.id}"
            val requirement = suite.requirement(scenario.requirement)
            if (requirement == null) problems += "$where references unknown requirement ${scenario.requirement}"
            val targets = requirement?.targets.orEmpty().filter(scenario::runsOn)
            scenario.targets.filter { it !in requirement?.targets.orEmpty() }.forEach { problems += "$where runs on ${it.wire}, which is not a target of ${scenario.requirement}" }
            scenario.withCapabilities.forEach { if (!PROFILE_NAME.matches(it) || it == Requirement.CORE) problems += "$where needs $it, expected a capability name" }
            scenario.withoutCapabilities.forEach { if (!PROFILE_NAME.matches(it) || it == Requirement.CORE) problems += "$where tests the absence of $it, expected a capability name" }
            scenario.withCapabilities.intersect(scenario.withoutCapabilities.toSet()).forEach { problems += "$where both needs and excludes $it" }
            if (scenario.title.isBlank()) problems += "$where has no title"
            if (scenario.steps.isEmpty()) problems += "$where has no step"
            targets.filter { target -> scenario.steps.none { it.runsOn(target) } }.forEach { problems += "$where has no step for target ${it.wire}" }
            scenario.steps.forEachIndexed { index, step ->
                val at = "$where step ${step.name ?: index + 1}"
                problems += stepProblems(suite, step, at)
                step.targets.filter { it !in targets }.forEach { problems += "$at runs on ${it.wire}, which is not a target of the scenario" }
                step.parallel.forEachIndexed { position, inner ->
                    val within = "$at parallel ${inner.name ?: position + 1}"
                    problems += stepProblems(suite, inner, within)
                    if (inner.request == null || listOfNotNull(inner.await, inner.control, inner.check, inner.webhook, inner.receiver).isNotEmpty() || inner.parallel.isNotEmpty()) problems += "$within must be a request"
                    if (inner.targets.isNotEmpty()) problems += "$within sets targets, which only a top-level step may"
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
        step.check?.let { if (checks.find(it.name) == null) problems += "$at calls unknown check ${it.name}" }
        val webhook = step.webhook
        val spans =
            listOfNotNull(
                step.await?.within, step.await?.every, step.control?.settled?.within, step.control?.settled?.every,
                webhook?.within, webhook?.during, webhook?.every, webhook?.notSooner, step.receiver?.reply?.delay,
            )
        spans.forEach { text -> runCatching { durations.parse(text) }.onFailure { problems += "$at: ${it.message}" } }
        if (listOfNotNull(step.request, step.await, step.control, step.check, step.webhook, step.receiver).isEmpty() && step.parallel.isEmpty()) problems += "$at does nothing"
        if (step.expect != null && listOfNotNull(step.request, step.await, step.webhook).isEmpty()) problems += "$at expects without a request or a delivery"
        if (listOfNotNull(step.request, step.await, step.webhook).size > 1) problems += "$at combines a request, an await and a delivery"
        webhook?.let {
            if (it.within != null && it.during != null) problems += "$at waits within and holds during at once"
            if (it.pick < 1) problems += "$at picks delivery ${it.pick}, expected 1 or more"
            if ((it.notSooner == null) != (it.after == null)) problems += "$at needs both after and not-sooner"
        }
        step.receiver?.reply?.let { if (it.status !in REPLY_STATUSES) problems += "$at replies ${it.status}, expected an HTTP status" }
        pointers(step, expectations).filterNot(::isTemplate).filterNot(::isPointer).forEach { problems += "$at names $it, expected a JSON Pointer" }
        patterns(step, expectations).filterNot(::isTemplate).forEach { pattern ->
            try {
                Regex(pattern)
            } catch (exception: PatternSyntaxException) {
                problems += "$at matches $pattern, which is not a regular expression: ${exception.description}"
            }
        }
        return problems
    }

    private fun pointers(
        step: Step,
        expectations: List<Expectation>,
    ): List<String> =
        expectations.flatMap { it.equals.keys + it.matches.keys + it.present + it.absent + it.headerEqualsMember.values } +
            step.capture.values + step.check?.pointer.let(::listOfNotNull) + step.webhook?.where?.keys.orEmpty() + step.receiver?.reply?.where?.keys.orEmpty()

    private fun patterns(
        step: Step,
        expectations: List<Expectation>,
    ): List<String> = expectations.flatMap { it.matches.values + it.headersMatch.values } + listOfNotNull(step.webhook?.path)

    private fun isTemplate(text: String): Boolean = TEMPLATE in text

    private fun isPointer(text: String): Boolean = text.isEmpty() || text.startsWith("/")

    private fun flatten(expectation: Expectation): List<Expectation> = listOf(expectation) + expectation.anyOf.flatMap(::flatten)

    private companion object {
        val SPEC_REFERENCE = Regex("^[a-z-]+#[0-9]+(\\.[0-9]+)*$")
        val PROFILE_NAME = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$")
        const val SECURITY_LIMIT = "conformance#8.1"
        val REPLY_STATUSES = 100..599
        const val TEMPLATE = "{{"
    }
}

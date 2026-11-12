package org.openfsp.validator.engine

import org.openfsp.validator.suite.Requirement
import org.openfsp.validator.suite.Scenario

enum class Verdict {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    INCONCLUSIVE,
    ASSISTED,
}

data class Failure(
    val step: String,
    val mismatches: List<Mismatch>,
    val exchange: RedactedExchange?,
)

data class ScenarioResult(
    val scenario: Scenario,
    val requirement: Requirement,
    val verdict: Verdict,
    val reason: String? = null,
    val failure: Failure? = null,
    val observations: List<String> = emptyList(),
)

enum class LevelVerdict {
    PASS,
    FAIL,
    INCOMPLETE,
    NOT_APPLICABLE,
    ;

    companion object {
        fun of(results: List<ScenarioResult>): LevelVerdict {
            val counted = results.filter { it.verdict != Verdict.NOT_APPLICABLE }
            return when {
                counted.any { it.verdict == Verdict.FAIL } -> FAIL
                counted.isEmpty() -> NOT_APPLICABLE
                counted.all { it.verdict == Verdict.PASS } -> PASS
                else -> INCOMPLETE
            }
        }
    }
}

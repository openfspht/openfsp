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

enum class ReasonCode(
    val wire: String,
) {
    PROVIDER_NOT_ANNOUNCED("provider-not-announced"),
    CAPABILITY_NOT_ANNOUNCED("capability-not-announced"),
    CAPABILITY_EXCLUDED("capability-excluded"),
    NO_APPLICABLE_SCENARIO("no-applicable-scenario"),
}

data class ScenarioResult(
    val scenario: Scenario,
    val requirement: Requirement,
    val verdict: Verdict,
    val reason: String? = null,
    val failure: Failure? = null,
    val observations: List<String> = emptyList(),
    val reasonCode: ReasonCode? = null,
) {
    init {
        require(verdict != Verdict.NOT_APPLICABLE || (reasonCode != null && !reason.isNullOrBlank())) {
            "a not applicable result of ${scenario.id} states its reason code and its reason"
        }
    }
}

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

package org.openfsp.validator.suite

import org.openfsp.validator.json.Json

enum class TargetKind(
    val wire: String,
) {
    SERVER("server"),
    CLIENT("client"),
    ;

    companion object {
        fun of(wire: String): TargetKind = entries.firstOrNull { it.wire == wire } ?: throw IllegalArgumentException("unknown target kind $wire, expected one of ${entries.joinToString { it.wire }}")
    }
}

data class Catalogue(
    val suite: SuiteInfo,
    val limits: List<Limit>,
    val requirements: List<Requirement>,
)

data class SuiteInfo(
    val version: String,
    val protocolVersion: String,
    val discovery: DiscoveryProbe,
    val capabilities: CapabilitiesProbe,
)

data class DiscoveryProbe(
    val path: String,
    val protocolVersion: String,
)

data class CapabilitiesProbe(
    val path: String,
    val providers: String,
    val id: String,
    val names: String,
)

data class Limit(
    val reference: String,
    val text: String,
)

data class Requirement(
    val id: String,
    val requirement: String,
    val level: String,
    val targets: List<TargetKind>,
    val title: String,
    val finding: String? = null,
    val levels: Map<String, String> = emptyMap(),
) {
    val isCore: Boolean get() = level == CORE

    fun on(target: TargetKind): Requirement = levels[target.wire]?.let { copy(level = it) } ?: this

    companion object {
        const val CORE = "core"
    }
}

data class Scenario(
    val requirement: String,
    val title: String,
    val variables: Map<String, String> = emptyMap(),
    val assisted: Assistance? = null,
    val targets: List<TargetKind> = emptyList(),
    val withCapabilities: List<String> = emptyList(),
    val withoutCapabilities: List<String> = emptyList(),
    val steps: List<Step>,
    val id: String = "",
) {
    fun runsOn(target: TargetKind): Boolean = targets.isEmpty() || target in targets
}

data class Assistance(
    val action: String,
    val inputs: List<String> = emptyList(),
)

data class Step(
    val name: String? = null,
    val setup: Boolean = false,
    val request: RequestSpec? = null,
    val expect: Expectation? = null,
    val capture: Map<String, String> = emptyMap(),
    val captureHeaders: Map<String, String> = emptyMap(),
    val simulator: SimulatorAction? = null,
    val await: AwaitSpec? = null,
    val check: CheckSpec? = null,
    val parallel: List<Step> = emptyList(),
    val webhook: WebhookAssertion? = null,
    val receiver: ReceiverAction? = null,
    val targets: List<TargetKind> = emptyList(),
) {
    fun runsOn(target: TargetKind): Boolean = targets.isEmpty() || target in targets

    val drivesSimulator: Boolean get() = simulator != null
}

data class RequestSpec(
    val method: String? = null,
    val path: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: Json? = null,
    val rawBody: String? = null,
    val auth: String? = null,
    val sameAs: String? = null,
) {
    companion object {
        const val NO_AUTH = "none"
        const val PREVIOUS = "previous"
    }
}

data class Expectation(
    val status: Int? = null,
    val mediaType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val headersPresent: List<String> = emptyList(),
    val headersAbsent: List<String> = emptyList(),
    val headerEqualsMember: Map<String, String> = emptyMap(),
    val schema: String? = null,
    val equals: Map<String, Json> = emptyMap(),
    val matches: Map<String, String> = emptyMap(),
    val present: List<String> = emptyList(),
    val absent: List<String> = emptyList(),
    val bodyContains: List<String> = emptyList(),
    val sameBodyAs: String? = null,
    val anyOf: List<Expectation> = emptyList(),
    val headersMatch: Map<String, String> = emptyMap(),
)

data class SimulatorAction(
    val declare: ReferenceScenario? = null,
    val default: ProviderScenario? = null,
    val resolve: Resolution? = null,
    val callback: ReferenceRef? = null,
    val closeStatement: StatementDay? = null,
    val statementEntry: StatementEntry? = null,
    val settled: Settlement? = null,
    val reset: Boolean = false,
)

data class Settlement(
    val provider: String,
    val reference: String,
    val callbacks: Int = 0,
    val within: String = "30s",
    val every: String = "500ms",
)

data class StatementDay(
    val provider: String,
    val day: String,
)

data class StatementEntry(
    val provider: String,
    val day: String,
    val amount: String,
    val bookedAt: String,
)

data class ReferenceScenario(
    val provider: String,
    val reference: String,
    val scenario: String,
)

data class ProviderScenario(
    val provider: String,
    val scenario: String,
)

data class Resolution(
    val provider: String,
    val reference: String,
    val outcome: String,
    val reason: String? = null,
)

data class ReferenceRef(
    val provider: String,
    val reference: String,
)

data class AwaitSpec(
    val request: RequestSpec,
    val until: Expectation,
    val within: String,
    val every: String = "500ms",
)

data class CheckSpec(
    val name: String,
    val pointer: String? = null,
    val from: String? = null,
)

data class WebhookAssertion(
    val method: String? = null,
    val path: String? = null,
    val where: Map<String, Json> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val count: Int? = null,
    val atLeast: Int? = null,
    val atMost: Int? = null,
    val pick: Int = 1,
    val within: String? = null,
    val during: String? = null,
    val every: String = "500ms",
    val after: String? = null,
    val notSooner: String? = null,
)

data class ReceiverAction(
    val reply: ReplyRule? = null,
)

data class ReplyRule(
    val where: Map<String, Json> = emptyMap(),
    val status: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val delay: String? = null,
    val times: Int? = null,
)

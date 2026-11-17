package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.problem.FieldProblem
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.run.spi.TargetConnection
import org.openfsp.validator.run.spi.TargetConnections
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.suite.spi.SuiteSource
import java.net.URI
import java.net.URISyntaxException

class RequestedRun(
    val configuration: Configuration,
    val connection: TargetConnection,
)

data class RunOrder(
    val target: String? = null,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val restrictedKey: String? = null,
    val inputs: Map<String, String> = emptyMap(),
)

@DomainService
class RunRequests(
    private val settings: RunSettings,
    private val source: SuiteSource,
    private val connections: TargetConnections,
) {
    val assistedInputs: Set<String> by lazy { source.suite().scenarios.flatMap { it.assisted?.inputs.orEmpty() }.toSet() }

    fun target(value: String?): TargetKind {
        val errors = mutableListOf<FieldProblem>()
        return target(value, errors) ?: throw ValidatorException(ProblemKind.INVALID_REQUEST, "Correct the fields listed in errors.", errors)
    }

    fun configuration(
        request: RunOrder,
        prepared: TargetKind,
    ): RequestedRun {
        val errors = mutableListOf<FieldProblem>()
        val target = request.target?.let { target(it, errors) } ?: prepared
        if (target != prepared) errors += FieldProblem("/target", "must be ${prepared.wire}, the target this run was prepared for")
        val url = url(request.baseUrl, errors)
        val apiKey = request.apiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) errors += FieldProblem("/api_key", "is required")
        if (apiKey.length > MAX_KEY_LENGTH) errors += FieldProblem("/api_key", "is too long")
        val restrictedKey = request.restrictedKey?.trim()?.takeIf { it.isNotEmpty() }
        if ((restrictedKey?.length ?: 0) > MAX_KEY_LENGTH) errors += FieldProblem("/restricted_key", "is too long")
        request.inputs.forEach { (name, value) ->
            if (name !in assistedInputs || name == RESTRICTED_KEY_INPUT) errors += FieldProblem("/inputs/$name", "is not an input of an assisted scenario")
            if (value.length > MAX_INPUT_LENGTH) errors += FieldProblem("/inputs/$name", "is too long")
        }
        if (errors.isNotEmpty() || url == null) throw ValidatorException(ProblemKind.INVALID_REQUEST, "Correct the fields listed in errors.", errors)

        val prefix = settings.simulator.keyPrefix
        listOfNotNull("/api_key" to apiKey, restrictedKey?.let { "/restricted_key" to it }).forEach { (pointer, key) ->
            if (!key.startsWith(prefix)) {
                throw ValidatorException(ProblemKind.TEST_KEY_REQUIRED, "Use a key of the ${settings.simulator.environment} environment, starting with $prefix.", listOf(FieldProblem(pointer, "must start with $prefix")))
            }
        }
        val configuration =
            Configuration(
                target = target,
                baseUrl = url.toString().trimEnd('/'),
                apiKey = apiKey,
                simulatorUrl = null,
                inputs = request.inputs + listOfNotNull(restrictedKey?.let { RESTRICTED_KEY_INPUT to it }),
                timeout = settings.requestTimeout,
            )
        return RequestedRun(configuration, connections.open(url, settings.requestTimeout))
    }

    private fun target(
        value: String?,
        errors: MutableList<FieldProblem>,
    ): TargetKind? {
        val target = TargetKind.entries.firstOrNull { it.wire == value?.trim() }
        if (target == null || target !in OFFERED_TARGETS) {
            errors += FieldProblem("/target", "must be one of ${OFFERED_TARGETS.joinToString { it.wire }}")
            return null
        }
        return target
    }

    private fun url(
        value: String?,
        errors: MutableList<FieldProblem>,
    ): URI? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) {
            errors += FieldProblem("/base_url", "is required")
            return null
        }
        val url =
            try {
                URI(text)
            } catch (_: URISyntaxException) {
                null
            }
        val acceptable =
            url != null && url.scheme?.lowercase() in SCHEMES && !url.host.isNullOrEmpty() && url.rawUserInfo == null && url.rawQuery == null && url.rawFragment == null
        if (!acceptable || text.length > MAX_URL_LENGTH) {
            errors += FieldProblem("/base_url", "must be an absolute http or https URL without credentials, query or fragment")
            return null
        }
        return url
    }

    companion object {
        const val RESTRICTED_KEY_INPUT = "restricted-key"
        val OFFERED_TARGETS = listOf(TargetKind.SERVER)
        private val SCHEMES = setOf("http", "https")
        private const val MAX_KEY_LENGTH = 512
        private const val MAX_URL_LENGTH = 2048
        private const val MAX_INPUT_LENGTH = 4096
    }
}

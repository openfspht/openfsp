package org.openfsp.validator.web.runs

import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.suite.TargetKind
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.FieldProblem
import org.openfsp.validator.web.problems.ProblemKind
import org.openfsp.validator.web.problems.ValidatorException
import org.openfsp.validator.web.suite.ConformanceSuite
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URISyntaxException
import java.util.Base64

data class RunRequest(
    val target: String? = null,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val restrictedKey: String? = null,
    val derivationKey: String? = null,
    val inputs: Map<String, String> = emptyMap(),
)

@Component
class RunRequests(
    private val properties: ValidatorProperties,
    private val conformance: ConformanceSuite,
    private val guard: TargetGuard,
) {
    val assistedInputs: Set<String> by lazy { conformance.suite.scenarios.flatMap { it.assisted?.inputs.orEmpty() }.toSet() }

    fun configuration(request: RunRequest): Configuration {
        val errors = mutableListOf<FieldProblem>()
        val target = target(request.target, errors)
        val url = url(request.baseUrl, errors)
        val apiKey = request.apiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) errors += FieldProblem("/api_key", "is required")
        if (apiKey.length > MAX_KEY_LENGTH) errors += FieldProblem("/api_key", "is too long")
        val restrictedKey = request.restrictedKey?.trim()?.takeIf { it.isNotEmpty() }
        if ((restrictedKey?.length ?: 0) > MAX_KEY_LENGTH) errors += FieldProblem("/restricted_key", "is too long")
        val derivationKey = if (target == TargetKind.GATEWAY) derivationKey(request.derivationKey, errors) else null
        request.inputs.forEach { (name, value) ->
            if (name !in assistedInputs || name == RESTRICTED_KEY_INPUT) errors += FieldProblem("/inputs/$name", "is not an input of an assisted scenario")
            if (value.length > MAX_INPUT_LENGTH) errors += FieldProblem("/inputs/$name", "is too long")
        }
        if (errors.isNotEmpty() || target == null || url == null) throw ValidatorException(ProblemKind.INVALID_REQUEST, "Correct the fields listed in errors.", errors)

        val prefix = properties.simulator.keyPrefix
        listOfNotNull("/api_key" to apiKey, restrictedKey?.let { "/restricted_key" to it }).forEach { (pointer, key) ->
            if (!key.startsWith(prefix)) {
                throw ValidatorException(ProblemKind.TEST_KEY_REQUIRED, "Use a key of the ${properties.simulator.environment} environment, starting with $prefix.", listOf(FieldProblem(pointer, "must start with $prefix")))
            }
        }
        guard.check(url)

        return Configuration(
            target = target,
            baseUrl = url.toString().trimEnd('/'),
            apiKey = apiKey,
            simulatorUrl = null,
            derivationKey = derivationKey,
            conformance = conformance.suite.directory,
            inputs = request.inputs + listOfNotNull(restrictedKey?.let { RESTRICTED_KEY_INPUT to it }),
            timeout = properties.requestTimeout,
        )
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

    private fun derivationKey(
        value: String?,
        errors: MutableList<FieldProblem>,
    ): String? {
        val key = value?.trim()?.takeIf { it.isNotEmpty() } ?: properties.simulator.derivationKey ?: return null
        try {
            OrderIdDerivation(Base64.getDecoder().decode(key))
        } catch (_: IllegalArgumentException) {
            errors += FieldProblem("/derivation_key", "must be the base64 form of at least 32 bytes")
        }
        return key
    }

    companion object {
        const val RESTRICTED_KEY_INPUT = "restricted-key"
        val OFFERED_TARGETS = listOf(TargetKind.GATEWAY, TargetKind.NATIVE_OPERATOR)
        private val SCHEMES = setOf("http", "https")
        private const val MAX_KEY_LENGTH = 512
        private const val MAX_URL_LENGTH = 2048
        private const val MAX_INPUT_LENGTH = 4096
    }
}

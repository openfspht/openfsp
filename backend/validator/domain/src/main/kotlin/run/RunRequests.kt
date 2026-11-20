package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.Configuration
import org.openfsp.validator.engine.TransportException
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
    val controlEndpoint: String? = null,
    val restrictedKey: String? = null,
    val inputs: Map<String, String> = emptyMap(),
    val accessCode: String? = null,
)

@DomainService
class RunRequests(
    private val settings: RunSettings,
    private val source: SuiteSource,
    private val connections: TargetConnections,
    private val fixtures: ServerFixtures,
) {
    val assistedInputs: Set<String> by lazy { source.suite().scenarios.flatMap { it.assisted?.inputs.orEmpty() }.toSet() }

    fun target(value: String?): TargetKind {
        val errors = mutableListOf<FieldProblem>()
        return target(value, errors) ?: throw ValidatorException(ProblemKind.INVALID_REQUEST, "Correct the fields listed in errors.", errors)
    }

    fun configuration(
        request: RunOrder,
        run: PreparedRun,
        webhookUrl: String,
    ): RequestedRun {
        val errors = mutableListOf<FieldProblem>()
        val target = request.target?.let { target(it, errors) } ?: run.target
        if (target != run.target) errors += FieldProblem("/target", "must be ${run.target.wire}, the target this run was prepared for")
        val url = url(request.baseUrl, errors)
        val givenKey = request.apiKey?.trim()?.takeIf { it.isNotEmpty() }
        if ((givenKey?.length ?: 0) > MAX_KEY_LENGTH) errors += FieldProblem("/api_key", "is too long")
        val accessCode = request.accessCode?.trim()?.takeIf { it.isNotEmpty() }
        if ((accessCode?.length ?: 0) > MAX_KEY_LENGTH) errors += FieldProblem("/access_code", "is too long")
        val givenControl = request.controlEndpoint?.trim()?.takeIf { it.isNotEmpty() }?.trimEnd('/')
        if (givenKey != null && givenControl == null) errors += FieldProblem("/control_endpoint", "is required with api_key: the suite chooses payment outcomes through it")
        if (givenKey == null && givenControl != null) errors += FieldProblem("/control_endpoint", "goes with api_key; without a key the server announces its own")
        if (givenControl != null && url != null && !ControlEndpoints.sameOrigin(givenControl, url.toString())) errors += FieldProblem("/control_endpoint", "must share the origin of base_url")
        val restrictedKey = request.restrictedKey?.trim()?.takeIf { it.isNotEmpty() }
        if ((restrictedKey?.length ?: 0) > MAX_KEY_LENGTH) errors += FieldProblem("/restricted_key", "is too long")
        request.inputs.forEach { (name, value) ->
            if (name !in assistedInputs || name == RESTRICTED_KEY_INPUT) errors += FieldProblem("/inputs/$name", "is not an input of an assisted scenario")
            if (value.length > MAX_INPUT_LENGTH) errors += FieldProblem("/inputs/$name", "is too long")
        }
        if (errors.isNotEmpty() || url == null) throw ValidatorException(ProblemKind.INVALID_REQUEST, "Correct the fields listed in errors.", errors)

        requireTestKeys(listOfNotNull(givenKey?.let { "/api_key" to it }, restrictedKey?.let { "/restricted_key" to it }))
        val baseUrl = url.toString().trimEnd('/')
        val connection = connections.open(url, settings.requestTimeout)
        try {
            val provided =
                try {
                    if (givenKey == null) fixtures.obtain(connection, baseUrl, webhookUrl, accessCode, source.suite().schemas) else null
                } catch (exception: TransportException) {
                    throw ValidatorException(ProblemKind.UNREACHABLE, exception.message)
                }
            val apiKey = givenKey ?: provided?.apiKey ?: throw ValidatorException(ProblemKind.CREDENTIALS_REQUIRED, "Provide a test key of the server.")
            requireTestKeys(listOf("/api_key" to apiKey))
            val configuration =
                Configuration(
                    target = target,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    controlEndpoint = givenControl ?: provided?.controlEndpoint,
                    inputs = provided?.inputs.orEmpty().filterKeys { it in assistedInputs } + request.inputs + listOfNotNull(restrictedKey?.let { RESTRICTED_KEY_INPUT to it }),
                    timeout = settings.requestTimeout,
                    preparedBy = if (provided == null) Configuration.OPERATOR else Configuration.SERVER,
                )
            return RequestedRun(configuration, connection)
        } catch (exception: RuntimeException) {
            connection.close()
            throw exception
        }
    }

    private fun requireTestKeys(keys: List<Pair<String, String>>) {
        val prefix = TestKeys.PREFIX
        keys.forEach { (pointer, key) ->
            if (!key.startsWith(prefix)) {
                throw ValidatorException(ProblemKind.TEST_KEY_REQUIRED, "Use a test key, starting with $prefix.", listOf(FieldProblem(pointer, "must start with $prefix")))
            }
        }
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

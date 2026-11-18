package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.at
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException

data class Fixtures(
    val apiKey: String,
    val inputs: Map<String, String>,
    val controlEndpoint: String,
)

@DomainService
class ServerFixtures(
    private val json: JsonCodec,
    private val schemas: SchemaCheckers,
) {
    fun obtain(
        transport: Transport,
        baseUrl: String,
        webhookUrl: String?,
        accessCode: String?,
        suiteSchemas: Map<String, Json>,
    ): Fixtures? {
        val endpoint = endpoint(transport, baseUrl) ?: return null
        val headers = buildMap {
            put(CONTENT_TYPE, JSON)
            put(ACCEPT, JSON)
            accessCode?.let { put(AUTHORIZATION, "$BEARER$it") }
        }
        val body = json.write(JsonObject(listOfNotNull(webhookUrl?.let { WEBHOOK_URL to JsonString(it) }).toMap()))
        val answer = transport.send(OutgoingRequest(POST, endpoint, headers, body))
        if (answer.status == UNAUTHENTICATED) {
            throw ValidatorException(ProblemKind.ACCESS_CODE_REQUIRED, "Ask the operator of the server for its conformance access code.")
        }
        val document =
            answer.takeIf { it.status == CREATED }?.json(json) as? JsonObject
                ?: throw ValidatorException(ProblemKind.PREPARATION_FAILED, "The server answered ${answer.status} when asked to prepare this run.")
        val problems = schemas.of(suiteSchemas).problems(SCHEMA, document)
        if (problems.isNotEmpty()) throw ValidatorException(ProblemKind.PREPARATION_FAILED, "The preparation answer does not follow the $SCHEMA schema: ${problems.joinToString("; ")}")
        val apiKey = document[API_KEY]!!.string!!
        val inputs = (document[INPUTS] as? JsonObject)?.members.orEmpty().mapNotNull { (name, value) -> value.string?.let { name to it } }.toMap()
        val controlEndpoint =
            document[CONTROL_ENDPOINT]!!.string!!.takeIf { ControlEndpoints.sameOrigin(it, baseUrl) }?.trimEnd('/')
                ?: throw ValidatorException(ProblemKind.PREPARATION_FAILED, "The control_endpoint of the preparation answer does not share the origin of the server.")
        return Fixtures(apiKey, inputs, controlEndpoint)
    }

    private fun endpoint(
        transport: Transport,
        baseUrl: String,
    ): String? {
        val discovery = transport.send(OutgoingRequest(GET, baseUrl + DISCOVERY, mapOf(ACCEPT to JSON), null))
        val announced = discovery.takeIf { it.status == OK }?.json(json)?.at(ENDPOINT)?.string ?: return null
        return announced.takeIf { ControlEndpoints.sameOrigin(it, baseUrl) }
    }

    private companion object {
        const val SCHEMA = "fixtures"
        const val DISCOVERY = "/.well-known/openfsp-configuration"
        const val ENDPOINT = "/conformance_endpoint"
        const val WEBHOOK_URL = "webhook_url"
        const val API_KEY = "api_key"
        const val INPUTS = "inputs"
        const val CONTROL_ENDPOINT = "control_endpoint"
        const val GET = "GET"
        const val POST = "POST"
        const val ACCEPT = "Accept"
        const val CONTENT_TYPE = "Content-Type"
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val JSON = "application/json"
        const val OK = 200
        const val CREATED = 201
        const val UNAUTHENTICATED = 401
    }
}

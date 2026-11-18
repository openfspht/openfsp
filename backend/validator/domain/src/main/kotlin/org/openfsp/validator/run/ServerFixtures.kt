package org.openfsp.validator.run

import org.openfsp.ddd.DomainService
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.spi.Transport
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.at
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.json.string
import org.openfsp.validator.problem.ProblemKind
import org.openfsp.validator.problem.ValidatorException
import java.net.URI

data class Fixtures(
    val apiKey: String,
    val inputs: Map<String, String>,
)

@DomainService
class ServerFixtures(
    private val json: JsonCodec,
) {
    fun obtain(
        transport: Transport,
        baseUrl: String,
        webhookUrl: String?,
        accessCode: String?,
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
        val document = answer.takeIf { it.status == CREATED }?.json(json) as? JsonObject
        val apiKey = document?.get(API_KEY)?.string?.takeIf { it.isNotBlank() }
            ?: throw ValidatorException(ProblemKind.PREPARATION_FAILED, "The server answered ${answer.status} when asked to prepare this run.")
        val inputs = (document[INPUTS] as? JsonObject)?.members.orEmpty().mapNotNull { (name, value) -> value.string?.let { name to it } }.toMap()
        return Fixtures(apiKey, inputs)
    }

    private fun endpoint(
        transport: Transport,
        baseUrl: String,
    ): String? {
        val discovery = transport.send(OutgoingRequest(GET, baseUrl + DISCOVERY, mapOf(ACCEPT to JSON), null))
        val announced = discovery.takeIf { it.status == OK }?.json(json)?.at(ENDPOINT)?.string ?: return null
        return announced.takeIf { sameOrigin(it, baseUrl) }
    }

    private fun sameOrigin(
        first: String,
        second: String,
    ): Boolean {
        val one = runCatching { URI(first) }.getOrNull() ?: return false
        val other = URI(second)
        return one.scheme.equals(other.scheme, ignoreCase = true) && one.host.equals(other.host, ignoreCase = true) && one.port == other.port
    }

    private companion object {
        const val DISCOVERY = "/.well-known/openfsp-configuration"
        const val ENDPOINT = "/conformance_endpoint"
        const val WEBHOOK_URL = "webhook_url"
        const val API_KEY = "api_key"
        const val INPUTS = "inputs"
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

package org.openfsp.sdk.http

import org.openfsp.protocol.ProblemType
import org.openfsp.sdk.OpenFspConfig
import org.openfsp.sdk.OpenFspException
import org.openfsp.sdk.protocol.Json
import org.openfsp.sdk.protocol.Problems
import java.net.URLEncoder
import java.time.Duration

internal class Fetched<T>(
    val value: T,
    val maxAge: Duration?,
)

internal class Api(
    private val config: OpenFspConfig,
    private val transport: Transport,
    private val clock: OpenFspClock,
) {
    fun <T> get(
        path: String,
        type: Class<T>,
        query: Map<String, String> = emptyMap(),
        authenticated: Boolean = true,
    ): T = fetch(path, type, query, authenticated).value

    fun <T> fetch(
        path: String,
        type: Class<T>,
        query: Map<String, String> = emptyMap(),
        authenticated: Boolean = true,
    ): Fetched<T> = call(Call("GET", path, query, null, null, authenticated), type)

    fun <T> post(
        path: String,
        type: Class<T>,
        body: Any? = null,
        idempotencyKey: String? = null,
    ): T = call(Call("POST", path, emptyMap(), body?.let(Json::write), idempotencyKey, true), type).value

    private fun <T> call(
        call: Call,
        type: Class<T>,
    ): Fetched<T> {
        var attempt = 1
        while (true) {
            try {
                return attempt(call, type)
            } catch (failure: OpenFspException) {
                val delay = config.retryPolicy.delay(failure, attempt, clock.random()) ?: throw failure
                clock.sleep(delay)
                attempt++
            }
        }
    }

    private fun <T> attempt(
        call: Call,
        type: Class<T>,
    ): Fetched<T> {
        val response = transport.send(call.toRequest())
        if (response.status !in SUCCESS) throw Problems.from(response, clock.now())
        val value = Json.read(response.body, type) ?: throw Problems.unexpected(response.status, "The server answered with an unreadable body", response.header("Request-Id"))
        return Fetched(value, response.header("Cache-Control")?.let(::maxAge))
    }

    private fun maxAge(cacheControl: String): Duration? = MAX_AGE.find(cacheControl)?.groupValues?.get(1)?.toLongOrNull()?.let(Duration::ofSeconds)

    private fun Call.toRequest(): TransportRequest {
        val search = query.entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val headers =
            buildMap {
                put("Accept", "application/json, ${ProblemType.MEDIA_TYPE}")
                put("User-Agent", USER_AGENT)
                if (authenticated) put("Authorization", "Bearer ${config.apiKey}")
                if (idempotencyKey != null) put("Idempotency-Key", idempotencyKey)
                if (body != null) put("Content-Type", "application/json")
            }
        val uri = config.baseUrl.resolve(if (search.isEmpty()) path else "$path?$search")
        return TransportRequest(method, uri, headers, body, config.timeout)
    }

    private class Call(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val body: ByteArray?,
        val idempotencyKey: String?,
        val authenticated: Boolean,
    )

    private companion object {
        const val USER_AGENT = "openfsp-sdk-kotlin"
        val MAX_AGE = Regex("""(?:^|[,\s])max-age=(\d+)\s*(?:,|$)""", RegexOption.IGNORE_CASE)
        val SUCCESS = 200..299
    }
}

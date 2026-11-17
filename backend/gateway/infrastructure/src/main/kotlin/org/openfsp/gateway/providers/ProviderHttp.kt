package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.shared.web.RetryAfter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.time.Duration
import java.time.Instant

class ProviderAnswer(
    val status: Int,
    val body: ByteArray,
    val headers: HttpHeaders = HttpHeaders(),
) {
    val isSuccess: Boolean get() = status in SUCCESS

    fun rateLimited(
        provider: String,
        now: Instant,
    ): ProviderFailure.Unavailable? {
        if (status != TOO_MANY_REQUESTS) return null
        return ProviderFailure.Unavailable(retryAfter = retryAfter(now), message = "$provider answered $status")
    }

    fun retryAfter(now: Instant): Duration? = headers.getFirst(HttpHeaders.RETRY_AFTER)?.let { RetryAfter.parse(it, now) }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        val SUCCESS = 200..299
    }
}

class ProviderHttp(
    private val client: RestClient,
    private val json: ProviderJson,
) {
    fun post(
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
    ): ProviderAnswer =
        exchange {
            post()
                .uri { uri -> query.entries.fold(uri.path(path)) { builder, (name, value) -> builder.queryParam(name, value) }.build() }
                .headers { http -> headers.forEach(http::set) }
                .accept(MediaType.APPLICATION_JSON)
                .apply { body?.let { contentType(MediaType.APPLICATION_JSON).body(json.write(it)) } }
        }

    fun get(
        path: String,
        vararg variables: Any,
    ): ProviderAnswer = exchange { get().uri(path, *variables).accept(MediaType.APPLICATION_JSON) }

    private fun exchange(request: RestClient.() -> RestClient.RequestHeadersSpec<*>): ProviderAnswer =
        try {
            val response = client.request().retrieve().toEntity(ByteArray::class.java)
            ProviderAnswer(response.statusCode.value(), response.body ?: ByteArray(0), response.headers)
        } catch (exception: ResourceAccessException) {
            throw when (exception.cause) {
                is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ProviderFailure.Unavailable(exception)
                else -> ProviderFailure.Timeout(exception)
            }
        }
}

@Component
class ProviderHttpFactory(
    private val properties: ProviderProperties,
    private val json: ProviderJson,
) {
    fun create(
        baseUrl: URI,
        bearer: String? = null,
    ): ProviderHttp {
        val timeout = properties.providerTimeout
        val requests = JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) }
        val builder =
            RestClient
                .builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requests)
                .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
        if (bearer != null) builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        return ProviderHttp(builder.build(), json)
    }
}

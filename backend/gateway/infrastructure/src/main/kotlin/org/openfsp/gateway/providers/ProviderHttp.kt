package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderFailure
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

class ProviderAnswer(
    val status: Int,
    val body: ByteArray,
    val headers: HttpHeaders = HttpHeaders(),
) {
    val isSuccess: Boolean get() = status in SUCCESS

    val isServerError: Boolean get() = status in SERVER_ERROR

    private companion object {
        val SUCCESS = 200..299
        val SERVER_ERROR = 500..599
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

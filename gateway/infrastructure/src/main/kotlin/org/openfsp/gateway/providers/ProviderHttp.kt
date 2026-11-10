package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderFailure
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.time.Duration

data class ProviderAnswer(
    val status: Int,
    val body: ByteArray,
) {
    val isSuccess: Boolean get() = status in SUCCESS

    override fun equals(other: Any?): Boolean = other is ProviderAnswer && other.status == status && other.body.contentEquals(body)

    override fun hashCode(): Int = 31 * status + body.contentHashCode()

    private companion object {
        val SUCCESS = 200..299
    }
}

class ProviderHttp(
    baseUrl: URI,
    timeout: Duration,
) {
    val client: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl.toString())
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) })
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    fun exchange(request: RestClient.() -> RestClient.RequestHeadersSpec<*>): ProviderAnswer =
        try {
            val response = client.request().retrieve().toEntity(ByteArray::class.java)
            ProviderAnswer(response.statusCode.value(), response.body ?: ByteArray(0))
        } catch (exception: ResourceAccessException) {
            throw when (exception.cause) {
                is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ProviderFailure.Unavailable(exception)
                else -> ProviderFailure.Timeout(exception)
            }
        }
}

class SecretRedaction(
    secrets: Collection<String?>,
) {
    private val secrets = secrets.filterNotNull().filter { it.length >= MIN_SECRET }.sortedByDescending { it.length }

    fun redact(detail: Map<String, Any?>?): Map<String, Any?>? = detail?.mapValues { (_, value) -> redact(value) }

    private fun redact(value: Any?): Any? =
        when (value) {
            is String -> secrets.fold(value) { text, secret -> text.replace(secret, REDACTED) }
            is Map<*, *> -> value.entries.associate { (key, inner) -> key.toString() to redact(inner) }
            is List<*> -> value.map(::redact)
            else -> value
        }

    private companion object {
        const val REDACTED = "[redacted]"
        const val MIN_SECRET = 4
    }
}

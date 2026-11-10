package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderJson
import org.springframework.http.HttpHeaders
import java.time.Instant
import java.util.Base64

class MonCashTokens(
    private val http: ProviderHttp,
    private val json: ProviderJson,
    private val calendar: BusinessCalendar,
    settings: MonCashSettings,
    private val errors: MonCashErrors,
) {
    private data class AccessToken(
        val value: String,
        val renewAt: Instant,
    )

    private val credentials = Base64.getEncoder().encodeToString("${settings.clientId}:${settings.clientSecret}".toByteArray())
    private val basic = "Basic $credentials"

    @Volatile
    private var current: AccessToken? = null

    fun bearer(): String = "Bearer ${valid().value}"

    fun secrets(): List<String?> = listOf(credentials, current?.value)

    fun forget() {
        current = null
    }

    private fun valid(): AccessToken = current?.takeIf { calendar.now().isBefore(it.renewAt) } ?: issue().also { current = it }

    private fun issue(): AccessToken {
        val answer = http.post("/oauth/token", headers = mapOf(HttpHeaders.AUTHORIZATION to basic), query = GRANT)
        if (!answer.isSuccess) throw errors.rejected(answer)
        val token = json.read(answer.body, MonCashTokenResponse::class.java, PROVIDER)
        val value = token.accessToken ?: throw ProviderFailure.Rejected("MonCash returned no access token")
        val lifetime = (token.expiresIn ?: DEFAULT_LIFETIME_SECONDS).coerceAtLeast(MARGIN_SECONDS + 1)
        return AccessToken(value, calendar.now().plusSeconds(lifetime - MARGIN_SECONDS))
    }

    private companion object {
        const val PROVIDER = "MonCash"
        const val DEFAULT_LIFETIME_SECONDS = 59L
        const val MARGIN_SECONDS = 10L
        val GRANT = mapOf("grant_type" to "client_credentials", "scope" to "read,write")
    }
}

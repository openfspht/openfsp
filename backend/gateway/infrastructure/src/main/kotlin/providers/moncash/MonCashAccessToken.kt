package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.moncash.MonCashProvider.Companion.PROVIDER
import java.time.Instant
import java.util.Base64

class MonCashAccessToken(
    settings: MonCashSettings,
    private val calendar: BusinessCalendar,
) {
    private data class Issued(
        val value: String,
        val renewAt: Instant,
    )

    private val credentials: String = Base64.getEncoder().encodeToString("${settings.clientId}:${settings.clientSecret}".toByteArray())

    val basic: String = "Basic $credentials"

    @Volatile
    private var current: Issued? = null

    fun secrets(): List<String?> = listOf(credentials, current?.value)

    fun bearer(request: () -> MonCashTokenResponse): String {
        val valid = current?.takeIf { calendar.now().isBefore(it.renewAt) } ?: issued(request()).also { current = it }
        return "Bearer ${valid.value}"
    }

    fun forget() {
        current = null
    }

    private fun issued(token: MonCashTokenResponse): Issued {
        val value = token.accessToken ?: throw ProviderFailure.Rejected("$PROVIDER returned no access token")
        val lifetime = (token.expiresIn ?: DEFAULT_LIFETIME_SECONDS).coerceAtLeast(MARGIN_SECONDS + 1)
        return Issued(value, calendar.now().plusSeconds(lifetime - MARGIN_SECONDS))
    }

    private companion object {
        const val DEFAULT_LIFETIME_SECONDS = 59L
        const val MARGIN_SECONDS = 10L
    }
}

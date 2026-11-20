package org.openfsp.simulator.control.web

import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class InterfaceSessions(
    private val properties: SimulatorProperties,
    private val clock: Clock,
) {
    private val key = SecretKeySpec(ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes), ALGORITHM)
    private val secure = properties.publicBaseUrl.startsWith("https://")

    val controlKey: String? get() = properties.operators[INTERFACE_OPERATOR]?.credentials?.get(API_KEY)?.takeIf { it.isNotBlank() }

    fun open(): ResponseCookie {
        val expiresAt = clock.instant().plus(properties.ui.sessionLifetime).epochSecond
        return ResponseCookie
            .from(COOKIE, "$expiresAt.${signature(expiresAt.toString())}")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(InterfaceControlFilter.PATH)
            .maxAge(properties.ui.sessionLifetime)
            .build()
    }

    fun isValid(token: String?): Boolean {
        val expiresAt = token?.substringBefore('.', "")?.toLongOrNull() ?: return false
        val presented = token.substringAfter('.')
        val expected = signature(expiresAt.toString())
        return MessageDigest.isEqual(presented.toByteArray(), expected.toByteArray()) && clock.instant().epochSecond < expiresAt
    }

    private fun signature(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(value.toByteArray()))

    companion object {
        const val COOKIE = "simulator_ui_session"
        private const val ALGORITHM = "HmacSHA256"
        private const val KEY_BYTES = 32
        private const val INTERFACE_OPERATOR = "mock_openfsp"
        private const val API_KEY = "api-key"
    }
}

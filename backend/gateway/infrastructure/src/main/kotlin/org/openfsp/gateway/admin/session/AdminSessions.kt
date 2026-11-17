package org.openfsp.gateway.admin.session

import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class AdminSessions(
    private val properties: GatewayProperties,
    private val clock: Clock,
) {
    private val key = MessageDigest.getInstance(DIGEST).digest(LABEL + properties.derivationKeyBytes + properties.adminToken.orEmpty().toByteArray(Charsets.UTF_8))

    fun open(): ResponseCookie {
        val expiresAt = clock.instant().plus(properties.adminSessionLifetime).epochSecond.toString()
        return cookie("$expiresAt.${sign(expiresAt)}", properties.adminSessionLifetime)
    }

    fun close(): ResponseCookie = cookie("", Duration.ZERO)

    fun isValid(value: String?): Boolean {
        val (expiresAt, signature) = value?.split('.')?.takeIf { it.size == 2 } ?: return false
        val expiry = expiresAt.toLongOrNull() ?: return false
        return MessageDigest.isEqual(signature.toByteArray(), sign(expiresAt).toByteArray()) && clock.instant().epochSecond < expiry
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray()))
    }

    private fun cookie(
        value: String,
        lifetime: Duration,
    ): ResponseCookie =
        ResponseCookie
            .from(COOKIE, value)
            .httpOnly(true)
            .secure(!properties.developmentMode)
            .sameSite("Strict")
            .path("/")
            .maxAge(lifetime)
            .build()

    companion object {
        const val COOKIE = "openfsp_admin"
        private const val ALGORITHM = "HmacSHA256"
        private const val DIGEST = "SHA-256"
        private val LABEL = "openfsp admin session".toByteArray(Charsets.US_ASCII)
    }
}

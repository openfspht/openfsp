package org.openfsp.gateway.admin.session

import org.openfsp.gateway.admin.AdminSessionStore
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.security.ApplicationKey
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class AdminSessions(
    private val properties: GatewayProperties,
    private val clock: Clock,
    applicationKey: ApplicationKey,
    private val store: AdminSessionStore,
) {
    private val random = SecureRandom()

    private val key = MessageDigest.getInstance(DIGEST).digest(applicationKey.derive(ApplicationKey.Purpose.ADMIN_SESSIONS) + properties.adminToken.orEmpty().toByteArray(Charsets.UTF_8))

    fun open(): ResponseCookie {
        val now = clock.instant()
        val expiresAt = now.plus(properties.adminSessionLifetime)
        val id = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(ID_BYTES).also(random::nextBytes))
        store.open(id, expiresAt, now)
        val claims = "$id.${expiresAt.epochSecond}"
        return cookie("$claims.${sign(claims)}", properties.adminSessionLifetime)
    }

    fun close(value: String?): ResponseCookie {
        verifiedId(value)?.let(store::close)
        return cookie("", Duration.ZERO)
    }

    fun isValid(value: String?): Boolean = verifiedId(value)?.let { store.isOpen(it, clock.instant()) } ?: false

    private fun verifiedId(value: String?): String? {
        val (id, expiresAt, signature) = value?.split('.')?.takeIf { it.size == 3 } ?: return null
        val expiry = expiresAt.toLongOrNull() ?: return null
        val authentic = MessageDigest.isEqual(signature.toByteArray(), sign("$id.$expiresAt").toByteArray())
        return id.takeIf { authentic && clock.instant().epochSecond < expiry }
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
        private const val ID_BYTES = 32
    }
}

package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

@DomainService
class CallbackTokens {
    private val random = SecureRandom()

    fun generate(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

    fun digest(token: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII)))

    fun matches(
        token: String,
        digest: String?,
    ): Boolean = digest != null && MessageDigest.isEqual(digest(token).toByteArray(), digest.toByteArray())

    private companion object {
        const val TOKEN_BYTES = 16
    }
}

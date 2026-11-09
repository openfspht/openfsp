package org.openfsp.gateway.payment

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CallbackTokens {
    private const val TOKEN_BYTES = 16
    private val random = SecureRandom()

    fun generate(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

    fun digest(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))

    fun masked(path: String): String = path.replace(Regex("^(/callbacks/[^/]+)/[^/]+$"), "$1/***")
}

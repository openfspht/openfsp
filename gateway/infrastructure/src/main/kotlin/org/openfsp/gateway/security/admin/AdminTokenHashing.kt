package org.openfsp.gateway.security.admin

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminTokenHashing {
    private val encoder = Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS)

    fun hash(token: String): String = encoder.encode(token)!!

    fun matches(
        token: String,
        hash: String,
    ): Boolean = runCatching { encoder.matches(token, hash) }.getOrDefault(false)

    companion object {
        const val PREFIX = "\$argon2id\$"
        private const val SALT_BYTES = 32
        private const val HASH_BYTES = 32
        private const val PARALLELISM = 4
        private const val MEMORY_KIB = 65_536
        private const val ITERATIONS = 3
    }
}

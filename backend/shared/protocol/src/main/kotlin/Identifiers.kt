package org.openfsp.protocol

import java.nio.ByteBuffer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object ResourceIds {
    private val pattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun isValid(value: String): Boolean = pattern.matches(value)

    @OptIn(ExperimentalUuidApi::class)
    fun generate(prefix: String): String = "${prefix}_${Base32.crockford(Uuid.generateV7().toByteArray())}"
}

object IdempotencyKeys {
    private val pattern = Regex("^[A-Za-z0-9_-]{1,255}$")

    fun isValid(value: String): Boolean = pattern.matches(value)
}

object Base32 {
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val RFC4648_LOWER = "abcdefghijklmnopqrstuvwxyz234567"

    fun crockford(bytes: ByteArray): String = encode(bytes, CROCKFORD)

    fun rfc4648Lower(bytes: ByteArray): String = encode(bytes, RFC4648_LOWER)

    private fun encode(
        bytes: ByteArray,
        alphabet: String,
    ): String {
        val buffer = ByteBuffer.wrap(bytes)
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var bits = 0
        var value = 0
        while (buffer.hasRemaining()) {
            value = (value shl 8) or (buffer.get().toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(value ushr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(value shl (5 - bits)) and 0x1F])
        return out.toString()
    }
}

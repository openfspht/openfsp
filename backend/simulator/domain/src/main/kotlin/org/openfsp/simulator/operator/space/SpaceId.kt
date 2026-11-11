package org.openfsp.simulator.operator.space

import java.security.SecureRandom
import java.util.HexFormat

data class SpaceId(
    val value: String,
) {
    val isDefault: Boolean get() = this == DEFAULT

    override fun toString(): String = value

    companion object {
        val DEFAULT = SpaceId("default")
        private const val RANDOM_BYTES = 16
        private val SHAPE = Regex("[0-9a-f]{32}")
        private val RANDOM = SecureRandom()

        fun random(): SpaceId = SpaceId(HexFormat.of().formatHex(ByteArray(RANDOM_BYTES).also(RANDOM::nextBytes)))

        fun parse(text: String): SpaceId? = text.takeIf(SHAPE::matches)?.let(::SpaceId)
    }
}

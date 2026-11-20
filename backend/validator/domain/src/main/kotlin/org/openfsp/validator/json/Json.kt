package org.openfsp.validator.json

import java.math.BigDecimal
import java.math.BigInteger

sealed interface Json

data object JsonNull : Json

data class JsonBoolean(
    val value: Boolean,
) : Json

class JsonNumber(
    val value: BigDecimal,
    val integral: Boolean,
) : Json {
    fun bigIntegerOrNull(): BigInteger? = if (integral) value.toBigIntegerExact() else null

    override fun equals(other: Any?): Boolean = other is JsonNumber && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()

    companion object {
        fun of(value: Long) = JsonNumber(BigDecimal.valueOf(value), integral = true)

        fun of(value: BigInteger) = JsonNumber(BigDecimal(value), integral = true)
    }
}

data class JsonString(
    val value: String,
) : Json

data class JsonArray(
    val items: List<Json>,
) : Json

data class JsonObject(
    val members: Map<String, Json>,
) : Json {
    operator fun get(name: String): Json? = members[name]
}

val Json.string: String? get() = (this as? JsonString)?.value

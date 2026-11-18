package org.openfsp.validator.engine

import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.JsonCodec
import java.security.SecureRandom
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

class TemplateException(
    message: String,
) : RuntimeException(message)

class Variables(
    private val runId: String,
    private val scenarioIndex: Int,
    private val inputs: Map<String, String>,
    constants: Map<String, String>,
    private val json: JsonCodec,
) {
    private val values = mutableMapOf<String, Json?>()
    private val generated = mutableMapOf<String, String>()

    init {
        constants.forEach { (name, value) -> values[name] = JsonString(value) }
    }

    val secrets: Collection<String> get() = inputs.values

    fun capture(
        name: String,
        value: Json?,
    ) {
        values[name] = value
    }

    fun lookup(name: String): Json? =
        if (name in values) values[name] else inputs[name]?.let(::JsonString) ?: JsonString(generate(name))

    private fun generate(name: String): String {
        val family = name.substringBefore(SEPARATOR)
        val label = name.substringAfter(SEPARATOR, DEFAULT_LABEL)
        return when (family) {
            REFERENCE -> generated.getOrPut(name) { "cv-$runId-$scenarioIndex-$label" }
            KEY -> generated.getOrPut(name) { "cv-$runId-$scenarioIndex-$label-${random(KEY_ENTROPY)}" }
            RANDOM -> generated.getOrPut(name) { random(RANDOM_LENGTH) }
            TODAY -> generated.getOrPut(name) { today(label) }
            else -> throw TemplateException("unknown template variable {{$name}}")
        }
    }

    fun resolve(text: String): String =
        PATTERN.replace(text) { match ->
            when (val value = evaluate(match)) {
                null -> throw TemplateException("{{${match.groupValues[1]}}} was captured from an absent member")
                is JsonString -> value.value
                else -> json.text(value)
            }
        }

    private fun evaluate(match: MatchResult): Json? {
        val (name, operator, operand) = match.destructured
        val value = lookup(name)
        if (operator.isEmpty()) return value
        val offset = if (operator == PLUS) operand.toBigInteger() else -operand.toBigInteger()
        val number =
            when (value) {
                is JsonNumber -> value.bigIntegerOrNull()
                is JsonString -> value.value.toBigIntegerOrNull() ?: return shifted(name, value.value, offset.toLong())
                else -> null
            } ?: throw TemplateException("{{$name}} is neither an integer nor a date and cannot take $operator $operand")
        return JsonNumber.of(number + offset)
    }

    private fun shifted(
        name: String,
        date: String,
        days: Long,
    ): Json =
        try {
            JsonString(LocalDate.parse(date).plusDays(days).toString())
        } catch (_: DateTimeException) {
            throw TemplateException("{{$name}} is neither an integer nor a date and cannot be shifted by $days")
        }

    private fun today(zone: String): String =
        try {
            LocalDate.now(ZoneId.of(zone)).toString()
        } catch (exception: DateTimeException) {
            throw TemplateException("{{$TODAY$SEPARATOR$zone}} names no time zone: ${exception.message}")
        }

    fun resolve(node: Json): Json? =
        when (node) {
            is JsonString -> PATTERN.matchEntire(node.value).let { whole -> if (whole == null) JsonString(resolve(node.value)) else evaluate(whole) }
            is JsonObject -> JsonObject(node.members.entries.mapNotNull { (name, member) -> resolve(member)?.let { resolve(name) to it } }.toMap(LinkedHashMap()))
            is JsonArray -> JsonArray(node.items.mapNotNull(::resolve))
            else -> node
        }

    companion object {
        const val API_KEY = "api_key"
        private const val REFERENCE = "reference"
        private const val KEY = "key"
        private const val RANDOM = "random"
        private const val TODAY = "today"
        private const val PLUS = "+"
        private const val SEPARATOR = ":"
        private const val DEFAULT_LABEL = "main"
        private const val KEY_ENTROPY = 12
        private const val RANDOM_LENGTH = 40
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val PATTERN = Regex("""\{\{\s*([A-Za-z0-9_.:/-]+)(?:\s+([+-])\s+([0-9]+))?\s*}}""")
        private val source = SecureRandom()

        fun random(length: Int): String = buildString(length) { repeat(length) { append(ALPHABET[source.nextInt(ALPHABET.length)]) } }
    }
}

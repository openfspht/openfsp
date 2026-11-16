package org.openfsp.validator.engine

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
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
) {
    private val values = mutableMapOf<String, JsonNode>()
    private val generated = mutableMapOf<String, String>()

    init {
        constants.forEach { (name, value) -> values[name] = JsonNodeFactory.instance.stringNode(value) }
    }

    val secrets: Collection<String> get() = inputs.values

    fun capture(
        name: String,
        value: JsonNode,
    ) {
        values[name] = value
    }

    fun lookup(name: String): JsonNode =
        values[name] ?: inputs[name]?.let { JsonNodeFactory.instance.stringNode(it) } ?: JsonNodeFactory.instance.stringNode(generate(name))

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
            val value = evaluate(match)
            when {
                value.isMissingNode -> throw TemplateException("{{${match.groupValues[1]}}} was captured from an absent member")
                value.isString -> value.asString()
                else -> value.toString()
            }
        }

    private fun evaluate(match: MatchResult): JsonNode {
        val (name, operator, operand) = match.destructured
        val value = lookup(name)
        if (operator.isEmpty()) return value
        val number =
            when {
                value.isIntegralNumber -> value.bigIntegerValue()
                value.isString -> value.asString().toBigIntegerOrNull()
                else -> null
            } ?: throw TemplateException("{{$name}} is not an integer and cannot take $operator $operand")
        val result = if (operator == PLUS) number + operand.toBigInteger() else number - operand.toBigInteger()
        return if (result.bitLength() < Int.SIZE_BITS) JsonNodeFactory.instance.numberNode(result.toInt()) else JsonNodeFactory.instance.numberNode(result.toLong())
    }

    private fun today(zone: String): String =
        try {
            LocalDate.now(ZoneId.of(zone)).toString()
        } catch (exception: DateTimeException) {
            throw TemplateException("{{$TODAY$SEPARATOR$zone}} names no time zone: ${exception.message}")
        }

    fun resolve(node: JsonNode): JsonNode =
        when {
            node.isString -> {
                val text = node.asString()
                PATTERN.matchEntire(text)?.let(::evaluate) ?: JsonNodeFactory.instance.stringNode(resolve(text))
            }
            node.isObject -> {
                val copy: ObjectNode = JsonNodeFactory.instance.objectNode()
                node.propertyNames().forEach { copy.set(resolve(it), resolve(node.get(it))) }
                copy
            }
            node.isArray -> JsonNodeFactory.instance.arrayNode().also { array -> node.forEach { array.add(resolve(it)) } }
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

package org.openfsp.validator.engine

import org.openfsp.validator.engine.spi.SchemaChecker
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonNull
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.JsonCodec
import org.openfsp.validator.suite.Expectation

data class Mismatch(
    val expectation: String,
    val expected: String,
    val observed: String,
)

class Expectations(
    private val schemas: () -> SchemaChecker,
    private val json: JsonCodec,
) {
    fun evaluate(
        expectation: Expectation,
        exchange: Exchange,
        variables: Variables,
        earlier: (String) -> Exchange?,
    ): List<Mismatch> {
        val mismatches = mutableListOf<Mismatch>()
        val body by lazy { exchange.json(json) }

        expectation.status?.let { if (exchange.status != it) mismatches += Mismatch("status", "$it", "${exchange.status}") }
        expectation.mediaType?.let { expected ->
            val observed = exchange.header(CONTENT_TYPE)?.substringBefore(';')?.trim()?.lowercase()
            if (observed != expected.lowercase()) mismatches += Mismatch("media type", expected, observed ?: ABSENT)
        }
        expectation.headers.forEach { (name, value) ->
            val expected = variables.resolve(value)
            val observed = exchange.header(name)
            if (observed != expected) mismatches += Mismatch("header $name", expected, observed ?: ABSENT)
        }
        expectation.headersPresent.forEach { name -> if (exchange.header(name) == null) mismatches += Mismatch("header $name", PRESENT, ABSENT) }
        expectation.headersAbsent.forEach { name -> exchange.header(name)?.let { mismatches += Mismatch("header $name", ABSENT, it) } }
        expectation.headersMatch.forEach { (name, pattern) ->
            val regex = Regex(variables.resolve(pattern))
            val observed = exchange.header(name)
            if (observed == null || !regex.containsMatchIn(observed)) mismatches += Mismatch("header $name", "a value matching $regex", observed ?: ABSENT)
        }

        val needsBody =
            expectation.schema != null || expectation.equals.isNotEmpty() || expectation.matches.isNotEmpty() || expectation.headerEqualsMember.isNotEmpty() || expectation.present.isNotEmpty() || expectation.absent.isNotEmpty()
        if (needsBody && body == null) {
            mismatches += Mismatch("body", "a JSON document", exchange.text.ifEmpty { "an empty body" })
            return mismatches
        }

        expectation.schema?.let { name -> schemas().problems(name, body!!).forEach { mismatches += Mismatch("schema $name", "valid against $name", it) } }
        expectation.headerEqualsMember.forEach { (name, template) ->
            val pointer = variables.resolve(template)
            val member = Pointers.resolve(body!!, pointer).singleOrNull()?.node
            val observed = exchange.header(name)
            if (member !is JsonString || member.value != observed) {
                mismatches += Mismatch("header $name equals $pointer", member?.let(::render) ?: ABSENT, observed ?: ABSENT)
            }
        }
        expectation.equals.forEach { (template, value) ->
            val pointer = variables.resolve(template)
            val expected = variables.resolve(value)
            val located = Pointers.resolve(body!!, pointer)
            when {
                located.isEmpty() && !Pointers.isWildcard(pointer) -> if (expected != null) mismatches += Mismatch("equals $pointer", render(expected), ABSENT)
                else -> located.filter { it.node != expected }.forEach { mismatches += Mismatch("equals ${it.pointer}", render(expected), render(it.node)) }
            }
        }
        expectation.matches.forEach { (template, pattern) ->
            val pointer = variables.resolve(template)
            val regex = Regex(variables.resolve(pattern))
            val located = Pointers.resolve(body!!, pointer)
            if (located.isEmpty() && !Pointers.isWildcard(pointer)) mismatches += Mismatch("matches $pointer", "a value matching $regex", ABSENT)
            located.filterNot { regex.containsMatchIn(text(it.node)) }.forEach { mismatches += Mismatch("matches ${it.pointer}", "a value matching $regex", render(it.node)) }
        }
        expectation.present.map(variables::resolve).forEach { pointer -> if (Pointers.resolve(body!!, pointer).isEmpty()) mismatches += Mismatch("present $pointer", PRESENT, ABSENT) }
        expectation.absent.map(variables::resolve).forEach { pointer ->
            Pointers.resolve(body!!, pointer).filterNot { it.node == JsonNull }.forEach { mismatches += Mismatch("absent ${it.pointer}", ABSENT, render(it.node)) }
        }
        expectation.bodyContains.forEach { fragment ->
            val expected = variables.resolve(fragment)
            if (!exchange.text.contains(expected)) mismatches += Mismatch("body contains", expected, exchange.text)
        }
        expectation.sameBodyAs?.let { name ->
            val reference = earlier(name)
            when {
                reference == null -> mismatches += Mismatch("same body as $name", "the body of step $name", "no such step ran")
                !reference.body.contentEquals(exchange.body) -> mismatches += Mismatch("same body as $name", reference.text, exchange.text)
            }
        }
        if (expectation.anyOf.isNotEmpty()) {
            val alternatives = expectation.anyOf.map { evaluate(it, exchange, variables, earlier) }
            if (alternatives.none { it.isEmpty() }) {
                mismatches +=
                    Mismatch(
                        "any of ${alternatives.size} alternatives",
                        "one alternative met",
                        alternatives.mapIndexed { index, failed -> "${index + 1}: " + failed.joinToString("; ") { "${it.expectation} expected ${it.expected}, observed ${it.observed}" } }.joinToString(" | "),
                    )
            }
        }
        return mismatches
    }

    private fun text(node: Json): String = if (node is JsonString) node.value else json.text(node)

    private fun render(node: Json?): String = node?.let(json::text) ?: ABSENT

    companion object {
        const val CONTENT_TYPE = "Content-Type"
        const val ABSENT = "(absent)"
        const val PRESENT = "(present)"
    }
}

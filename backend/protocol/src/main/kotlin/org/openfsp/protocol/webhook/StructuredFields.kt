package org.openfsp.protocol.webhook

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Base64

internal sealed interface BareItem {
    data class IntegerItem(
        val value: Long,
    ) : BareItem

    data class DecimalItem(
        val value: BigDecimal,
    ) : BareItem

    data class StringItem(
        val value: String,
    ) : BareItem

    data class TokenItem(
        val value: String,
    ) : BareItem

    class ByteSequence(
        val value: ByteArray,
    ) : BareItem

    data class BooleanItem(
        val value: Boolean,
    ) : BareItem
}

internal sealed interface Member {
    val parameters: Map<String, BareItem>
}

internal data class Item(
    val value: BareItem,
    override val parameters: Map<String, BareItem>,
) : Member

internal data class InnerList(
    val items: List<Item>,
    override val parameters: Map<String, BareItem>,
) : Member

internal object StructuredFields {
    fun parseDictionary(input: String): Map<String, Member>? =
        try {
            Parser(input).dictionary()
        } catch (malformed: MalformedField) {
            null
        }

    fun serialize(list: InnerList): String = "(${list.items.joinToString(" ") { serialize(it.value) + serialize(it.parameters) }})${serialize(list.parameters)}"

    private fun serialize(parameters: Map<String, BareItem>): String =
        parameters.entries.joinToString("") { (key, value) -> if (value == BareItem.BooleanItem(true)) ";$key" else ";$key=${serialize(value)}" }

    private fun serialize(item: BareItem): String =
        when (item) {
            is BareItem.IntegerItem -> item.value.toString()
            is BareItem.DecimalItem -> item.value.setScale(DECIMAL_SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros().let { if (it.scale() < 1) it.setScale(1) else it }.toPlainString()
            is BareItem.StringItem -> "\"${item.value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is BareItem.TokenItem -> item.value
            is BareItem.ByteSequence -> ":${Base64.getEncoder().encodeToString(item.value)}:"
            is BareItem.BooleanItem -> if (item.value) "?1" else "?0"
        }

    private class MalformedField : RuntimeException(null, null, false, false)

    private class Parser(
        private val input: String,
    ) {
        private var position = 0

        fun dictionary(): Map<String, Member> {
            val members = LinkedHashMap<String, Member>()
            skip(' ')
            while (!atEnd()) {
                val key = key()
                members[key] = if (peek() == '=') advance().let { member() } else Item(BareItem.BooleanItem(true), parameters())
                skipWhitespace()
                if (atEnd()) return members
                expect(',')
                skipWhitespace()
                if (atEnd()) fail()
            }
            return members
        }

        private fun member(): Member = if (peek() == '(') innerList() else Item(bareItem(), parameters())

        private fun innerList(): InnerList {
            expect('(')
            val items = mutableListOf<Item>()
            while (!atEnd()) {
                skip(' ')
                if (peek() == ')') {
                    advance()
                    return InnerList(items, parameters())
                }
                items += Item(bareItem(), parameters())
                if (peek() != ' ' && peek() != ')') fail()
            }
            fail()
        }

        private fun parameters(): Map<String, BareItem> {
            val parameters = LinkedHashMap<String, BareItem>()
            while (peek() == ';') {
                advance()
                skip(' ')
                val key = key()
                parameters[key] = if (peek() == '=') advance().let { bareItem() } else BareItem.BooleanItem(true)
            }
            return parameters
        }

        private fun key(): String {
            val first = peek() ?: fail()
            if (!first.isLowercaseLetter() && first != '*') fail()
            val start = position
            while (peek()?.let { it.isLowercaseLetter() || it.isAsciiDigit() || it in KEY_SYMBOLS } == true) advance()
            return input.substring(start, position)
        }

        private fun bareItem(): BareItem {
            val next = peek() ?: fail()
            return when {
                next == '-' || next.isAsciiDigit() -> number()
                next == '"' -> string()
                next == ':' -> byteSequence()
                next == '?' -> boolean()
                next.isAsciiLetter() || next == '*' -> token()
                else -> fail()
            }
        }

        private fun number(): BareItem {
            val start = position
            if (peek() == '-') advance()
            if (peek()?.isAsciiDigit() != true) fail()
            var decimal = false
            while (true) {
                val next = peek()
                when {
                    next != null && next.isAsciiDigit() -> advance()
                    next == '.' && !decimal -> {
                        if (position - start > MAX_DECIMAL_INTEGER_DIGITS + if (input[start] == '-') 1 else 0) fail()
                        decimal = true
                        advance()
                    }
                    else -> break
                }
                val digits = position - start - if (input[start] == '-') 1 else 0
                if (!decimal && digits > MAX_INTEGER_DIGITS) fail()
                if (decimal && digits > MAX_DECIMAL_DIGITS) fail()
            }
            val text = input.substring(start, position)
            if (!decimal) return BareItem.IntegerItem(text.toLong())
            val fraction = text.substringAfter('.')
            if (fraction.isEmpty() || fraction.length > MAX_FRACTION_DIGITS) fail()
            return BareItem.DecimalItem(BigDecimal(text))
        }

        private fun string(): BareItem {
            expect('"')
            val value = StringBuilder()
            while (true) {
                val next = peek() ?: fail()
                advance()
                when {
                    next == '\\' -> {
                        val escaped = peek() ?: fail()
                        if (escaped != '"' && escaped != '\\') fail()
                        value.append(escaped)
                        advance()
                    }
                    next == '"' -> return BareItem.StringItem(value.toString())
                    next.code !in PRINTABLE -> fail()
                    else -> value.append(next)
                }
            }
        }

        private fun token(): BareItem {
            val start = position
            advance()
            while (peek()?.let { it.isAsciiLetter() || it.isAsciiDigit() || it in TOKEN_SYMBOLS } == true) advance()
            return BareItem.TokenItem(input.substring(start, position))
        }

        private fun byteSequence(): BareItem {
            expect(':')
            val start = position
            while (peek()?.let { it.isAsciiLetter() || it.isAsciiDigit() || it in BASE64_SYMBOLS } == true) advance()
            val encoded = input.substring(start, position)
            expect(':')
            return BareItem.ByteSequence(runCatching { Base64.getDecoder().decode(encoded) }.getOrElse { fail() })
        }

        private fun boolean(): BareItem {
            expect('?')
            return when (peek()) {
                '1' -> BareItem.BooleanItem(true)
                '0' -> BareItem.BooleanItem(false)
                else -> fail()
            }.also { advance() }
        }

        private fun peek(): Char? = input.getOrNull(position)

        private fun advance() {
            position++
        }

        private fun atEnd() = position >= input.length

        private fun expect(character: Char) {
            if (peek() != character) fail()
            advance()
        }

        private fun skip(character: Char) {
            while (peek() == character) advance()
        }

        private fun skipWhitespace() {
            while (peek() == ' ' || peek() == '\t') advance()
        }

        private fun fail(): Nothing = throw MalformedField()

        private fun Char.isLowercaseLetter() = this in 'a'..'z'

        private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }

    private const val DECIMAL_SCALE = 3
    private const val MAX_INTEGER_DIGITS = 15
    private const val MAX_DECIMAL_DIGITS = 16
    private const val MAX_DECIMAL_INTEGER_DIGITS = 12
    private const val MAX_FRACTION_DIGITS = 3
    private const val KEY_SYMBOLS = "_-.*"
    private const val TOKEN_SYMBOLS = "!#$%&'*+-.^_`|~:/"
    private const val BASE64_SYMBOLS = "+/="
    private val PRINTABLE = 0x20..0x7e
}

package org.openfsp.validator.json.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonBoolean
import org.openfsp.validator.json.JsonNull
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.JsonCodec
import java.math.BigDecimal

@Stub
object PlainJsonCodec : JsonCodec {
    override fun parse(bytes: ByteArray): Json? = runCatching { Reader(String(bytes, Charsets.UTF_8)).document() }.getOrNull()

    override fun write(value: Json): ByteArray = StringBuilder().also { write(value, it) }.toString().toByteArray(Charsets.UTF_8)

    private fun write(
        value: Json,
        out: StringBuilder,
    ) {
        when (value) {
            JsonNull -> out.append("null")
            is JsonBoolean -> out.append(value.value)
            is JsonNumber -> out.append(value.value.toString())
            is JsonString -> string(value.value, out)
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            is JsonObject -> {
                out.append('{')
                value.members.entries.forEachIndexed { index, (name, member) ->
                    if (index > 0) out.append(',')
                    string(name, out)
                    out.append(':')
                    write(member, out)
                }
                out.append('}')
            }
        }
    }

    private fun string(
        value: String,
        out: StringBuilder,
    ) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    private class Reader(
        private val text: String,
    ) {
        private var position = 0

        fun document(): Json = value().also { require(skip() == text.length) { "trailing content" } }

        private fun skip(): Int {
            while (position < text.length && text[position].isWhitespace()) position++
            return position
        }

        private fun value(): Json {
            skip()
            require(position < text.length) { "unexpected end" }
            return when (text[position]) {
                '{' -> members()
                '[' -> items()
                '"' -> JsonString(string())
                't' -> literal("true", JsonBoolean(true))
                'f' -> literal("false", JsonBoolean(false))
                'n' -> literal("null", JsonNull)
                else -> number()
            }
        }

        private fun members(): Json {
            position++
            val members = linkedMapOf<String, Json>()
            if (text[skip()] == '}') return JsonObject(members).also { position++ }
            while (true) {
                skip()
                val name = string()
                expect(':')
                members[name] = value()
                if (next() == '}') return JsonObject(members)
            }
        }

        private fun items(): Json {
            position++
            val items = mutableListOf<Json>()
            if (text[skip()] == ']') return JsonArray(items).also { position++ }
            while (true) {
                items += value()
                if (next() == ']') return JsonArray(items)
            }
        }

        private fun next(): Char {
            val char = text[skip()]
            require(char == ',' || char == '}' || char == ']') { "unexpected $char" }
            position++
            return char
        }

        private fun expect(char: Char) {
            require(text[skip()] == char) { "expected $char" }
            position++
        }

        private fun literal(
            word: String,
            value: Json,
        ): Json {
            require(text.startsWith(word, position)) { "unexpected token" }
            position += word.length
            return value
        }

        private fun number(): Json {
            val start = position
            while (position < text.length && text[position] in NUMBER) position++
            val literal = text.substring(start, position)
            return JsonNumber(BigDecimal(literal), literal.none { it == '.' || it == 'e' || it == 'E' })
        }

        private fun string(): String {
            require(text[position] == '"') { "expected a string" }
            position++
            val out = StringBuilder()
            while (true) {
                when (val char = text[position++]) {
                    '"' -> return out.toString()
                    '\\' ->
                        when (val escaped = text[position++]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> out.append(text.substring(position, position + 4).toInt(16).toChar()).also { position += 4 }
                            else -> out.append(escaped)
                        }
                    else -> out.append(char)
                }
            }
        }

        private companion object {
            const val NUMBER = "+-0123456789.eE"
        }
    }
}

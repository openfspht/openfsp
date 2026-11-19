package org.openfsp.validator.engine

import org.openfsp.ddd.DomainService
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonBoolean
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString

data class Located(
    val pointer: String,
    val node: Json,
)

@DomainService
class Pointers {
    fun isWildcard(pointer: String): Boolean = tokens(pointer).contains(WILDCARD)

    fun resolve(
        root: Json,
        pointer: String,
    ): List<Located> {
        var current = listOf(Located("", root))
        for (token in tokens(pointer)) {
            current =
                current.flatMap { (path, node) ->
                    val filter = FILTER.matchEntire(token)
                    when {
                        filter != null && node is JsonArray ->
                            node.items.withIndex().filter { (_, item) -> matches(member(item, filter.groupValues[1]), filter.groupValues[2]) }.map { (index, item) -> Located("$path/$index", item) }
                        token == WILDCARD && node is JsonArray -> node.items.mapIndexed { index, item -> Located("$path/$index", item) }
                        token == WILDCARD && node is JsonObject -> node.members.map { (name, member) -> Located("$path/${escape(name)}", member) }
                        node is JsonArray -> token.toIntOrNull()?.let { index -> node.items.getOrNull(index)?.let { listOf(Located("$path/$token", it)) } } ?: emptyList()
                        node is JsonObject -> node[token]?.let { listOf(Located("$path/${escape(token)}", it)) } ?: emptyList()
                        else -> emptyList()
                    }
                }
        }
        return current
    }

    private fun member(
        element: Json,
        path: String,
    ): Json? = if (path.isEmpty()) element else path.split("/").fold(element as Json?) { node, name -> (node as? JsonObject)?.get(name) }

    private fun matches(
        member: Json?,
        value: String,
    ): Boolean =
        when (member) {
            is JsonString -> member.value == value
            is JsonNumber -> member.toString() == value
            is JsonBoolean -> member.value.toString() == value
            else -> false
        }

    fun isValidPointer(pointer: String): Boolean = pointer.isEmpty() || (pointer.startsWith("/") && !BAD_ESCAPE.containsMatchIn(pointer))

    private fun tokens(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        require(pointer.startsWith("/")) { "JSON Pointer $pointer must start with /" }
        return pointer.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    }

    private fun escape(token: String): String = token.replace("~", "~0").replace("/", "~1")

    companion object {
        const val WILDCARD = "*"
        private val FILTER = Regex("""^\[([^=\]]*)=(.*)]$""")
        private val BAD_ESCAPE = Regex("~[^01]|~$")
    }
}

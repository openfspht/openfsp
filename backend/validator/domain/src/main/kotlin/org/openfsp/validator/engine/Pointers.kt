package org.openfsp.validator.engine

import tools.jackson.databind.JsonNode

data class Located(
    val pointer: String,
    val node: JsonNode,
)

object Pointers {
    const val WILDCARD = "*"
    private val FILTER = Regex("""^\[([^=\]]*)=(.*)]$""")

    fun isWildcard(pointer: String): Boolean = tokens(pointer).contains(WILDCARD)

    fun resolve(
        root: JsonNode,
        pointer: String,
    ): List<Located> {
        var current = listOf(Located("", root))
        for (token in tokens(pointer)) {
            current =
                current.flatMap { (path, node) ->
                    val filter = FILTER.matchEntire(token)
                    when {
                        filter != null && node.isArray ->
                            (0 until node.size()).filter { matches(member(node.get(it), filter.groupValues[1]), filter.groupValues[2]) }.map { Located("$path/$it", node.get(it)) }
                        token == WILDCARD && node.isArray -> (0 until node.size()).map { Located("$path/$it", node.get(it)) }
                        token == WILDCARD && node.isObject -> node.propertyNames().map { Located("$path/${escape(it)}", node.get(it)) }
                        node.isArray -> token.toIntOrNull()?.takeIf { it in 0 until node.size() }?.let { listOf(Located("$path/$token", node.get(it))) } ?: emptyList()
                        node.isObject -> node.get(token)?.let { listOf(Located("$path/${escape(token)}", it)) } ?: emptyList()
                        else -> emptyList()
                    }
                }
        }
        return current
    }

    private fun member(
        element: JsonNode,
        path: String,
    ): JsonNode? = if (path.isEmpty()) element else path.split("/").fold(element as JsonNode?) { node, name -> node?.takeIf { it.isObject }?.get(name) }

    private fun matches(
        member: JsonNode?,
        value: String,
    ): Boolean = member != null && (member.isString || member.isNumber || member.isBoolean) && (if (member.isString) member.asString() else member.toString()) == value

    fun isValidPointer(pointer: String): Boolean = pointer.isEmpty() || (pointer.startsWith("/") && !Regex("~[^01]|~$").containsMatchIn(pointer))

    private fun tokens(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        require(pointer.startsWith("/")) { "JSON Pointer $pointer must start with /" }
        return pointer.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    }

    private fun escape(token: String): String = token.replace("~", "~0").replace("/", "~1")
}

package org.openfsp.validator.engine

import tools.jackson.databind.JsonNode

data class Located(
    val pointer: String,
    val node: JsonNode,
)

object Pointers {
    const val WILDCARD = "*"

    fun isWildcard(pointer: String): Boolean = tokens(pointer).contains(WILDCARD)

    fun resolve(
        root: JsonNode,
        pointer: String,
    ): List<Located> {
        var current = listOf(Located("", root))
        for (token in tokens(pointer)) {
            current =
                current.flatMap { (path, node) ->
                    when {
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

    fun isValidPointer(pointer: String): Boolean = pointer.isEmpty() || (pointer.startsWith("/") && !Regex("~[^01]|~$").containsMatchIn(pointer))

    private fun tokens(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        require(pointer.startsWith("/")) { "JSON Pointer $pointer must start with /" }
        return pointer.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    }

    private fun escape(token: String): String = token.replace("~", "~0").replace("/", "~1")
}

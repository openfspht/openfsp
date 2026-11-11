package org.openfsp.validator.engine

import org.openfsp.validator.suite.CheckSpec
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

fun interface Check {
    fun run(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonMapper,
    ): List<Mismatch>
}

object Checks {
    const val POINTERS_RESOLVE_IN_REQUEST = "pointers-resolve-in-request"

    private val registry: Map<String, Check> =
        mapOf(
            POINTERS_RESOLVE_IN_REQUEST to Check(::pointersResolveInRequest),
        )

    val names: Set<String> get() = registry.keys

    fun find(name: String): Check? = registry[name]

    private fun pointersResolveInRequest(
        spec: CheckSpec,
        exchange: Exchange,
        json: JsonMapper,
    ): List<Mismatch> {
        val selector = spec.pointer ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a pointer argument", "none"))
        val response = exchange.json(json) ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON response", exchange.text))
        val sent = exchange.request.body?.let { runCatching { json.readTree(it) }.getOrNull() } ?: return listOf(Mismatch(POINTERS_RESOLVE_IN_REQUEST, "a JSON request body", "none"))
        val pointers = Pointers.resolve(response, selector)
        return pointers.mapNotNull { (location, node) ->
            val label = "$POINTERS_RESOLVE_IN_REQUEST $location"
            when {
                !node.isString -> Mismatch(label, "a JSON Pointer string", node.toString())
                !Pointers.isValidPointer(node.asString()) -> Mismatch(label, "a JSON Pointer (RFC 6901)", node.asString())
                !designates(sent, node.asString()) -> Mismatch(label, "a location in the request body as sent", node.asString())
                else -> null
            }
        }
    }

    private fun designates(
        sent: JsonNode,
        pointer: String,
    ): Boolean {
        if (pointer.isEmpty() || Pointers.resolve(sent, pointer).isNotEmpty()) return true
        val parent = pointer.substringBeforeLast('/')
        return Pointers.resolve(sent, parent).singleOrNull()?.node?.isObject == true
    }
}

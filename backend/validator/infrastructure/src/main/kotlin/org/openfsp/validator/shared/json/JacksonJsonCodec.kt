package org.openfsp.validator.shared.json

import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonArray
import org.openfsp.validator.json.JsonBoolean
import org.openfsp.validator.json.JsonNull
import org.openfsp.validator.json.JsonNumber
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.JsonString
import org.openfsp.validator.json.spi.JsonCodec
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import java.math.BigDecimal

object JacksonJsonCodec : JsonCodec {
    private val mapper: JsonMapper = JsonMapper.builder().build()
    private val nodes: JsonNodeFactory = JsonNodeFactory.instance

    override fun parse(bytes: ByteArray): Json? = runCatching { mapper.readTree(bytes) }.getOrNull()?.takeUnless { it.isMissingNode }?.let(::toJson)

    override fun write(value: Json): ByteArray = mapper.writeValueAsBytes(toNode(value))

    fun toJson(node: JsonNode): Json =
        when {
            node.isObject -> JsonObject(node.propertyNames().associateWith { toJson(node.get(it)) })
            node.isArray -> JsonArray(node.values().map(::toJson))
            node.isString -> JsonString(node.asString())
            node.isIntegralNumber -> JsonNumber(BigDecimal(node.bigIntegerValue()), integral = true)
            node.isNumber -> JsonNumber(node.decimalValue(), integral = false)
            node.isBoolean -> JsonBoolean(node.booleanValue())
            node.isNull || node.isMissingNode -> JsonNull
            else -> JsonString(node.asString())
        }

    fun toNode(json: Json): JsonNode =
        when (json) {
            JsonNull -> nodes.nullNode()
            is JsonBoolean -> nodes.booleanNode(json.value)
            is JsonNumber -> json.bigIntegerOrNull()?.let(nodes::numberNode) ?: nodes.numberNode(json.value)
            is JsonString -> nodes.stringNode(json.value)
            is JsonArray -> nodes.arrayNode().also { array -> json.items.forEach { array.add(toNode(it)) } }
            is JsonObject -> nodes.objectNode().also { node -> json.members.forEach { (name, member) -> node.set(name, toNode(member)) } }
        }
}

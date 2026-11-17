package org.openfsp.validator.suites

import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.openfsp.validator.engine.spi.SchemaChecker
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.json.Json
import org.openfsp.validator.shared.json.JacksonJsonCodec
import tools.jackson.databind.JsonNode
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object NetworkntSchemaCheckers : SchemaCheckers {
    private val compiled: MutableMap<Map<String, Json>, NetworkntSchemas> = Collections.synchronizedMap(WeakHashMap())

    override fun of(schemas: Map<String, Json>): NetworkntSchemas = compiled.getOrPut(schemas) { NetworkntSchemas(schemas.mapValues { JacksonJsonCodec.toNode(it.value) }) }
}

class NetworkntSchemas(
    documents: Map<String, JsonNode>,
) : SchemaChecker {
    private val idByName: Map<String, String> = documents.mapValues { (name, document) -> document.get(ID)?.asString() ?: throw IllegalArgumentException("schema $name has no \$id") }
    private val registry =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
            builder.schemas(documents.entries.associate { (name, document) -> idByName.getValue(name) to document.toString() })
        }
    private val compiled = ConcurrentHashMap<String, Schema>()

    override fun problems(
        schema: String,
        instance: Json,
    ): List<String> = validate(schema, JacksonJsonCodec.toNode(instance))

    fun validate(
        name: String,
        instance: JsonNode,
    ): List<String> = schema(name).validate(instance).map { "${it.instanceLocation}: ${it.message}" }

    private fun schema(name: String): Schema = compiled.computeIfAbsent(name) { registry.getSchema(SchemaLocation.of(idByName[name] ?: throw IllegalArgumentException("unknown schema $name"))) }

    private companion object {
        const val ID = "\$id"
    }
}

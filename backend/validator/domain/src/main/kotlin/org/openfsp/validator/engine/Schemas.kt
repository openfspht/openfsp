package org.openfsp.validator.engine

import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import tools.jackson.databind.JsonNode

class Schemas(
    documents: Map<String, JsonNode>,
) {
    private val idByName: Map<String, String> = documents.mapValues { (name, document) -> document.get(ID)?.asString() ?: throw IllegalArgumentException("schema $name has no \$id") }
    private val registry =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) { builder ->
            builder.schemas(documents.entries.associate { (name, document) -> idByName.getValue(name) to document.toString() })
        }
    private val compiled = mutableMapOf<String, Schema>()

    val names: Set<String> get() = idByName.keys

    fun validate(
        name: String,
        instance: JsonNode,
    ): List<String> = schema(name).validate(instance).map { "${it.instanceLocation}: ${it.message}" }

    fun schema(name: String): Schema = compiled.getOrPut(name) { registry.getSchema(SchemaLocation.of(idByName[name] ?: throw IllegalArgumentException("unknown schema $name"))) }

    private companion object {
        const val ID = "\$id"
    }
}

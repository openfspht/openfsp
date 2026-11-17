package org.openfsp.validator.engine.spi

import org.openfsp.validator.json.Json

fun interface SchemaChecker {
    fun problems(
        schema: String,
        instance: Json,
    ): List<String>
}

fun interface SchemaCheckers {
    fun of(schemas: Map<String, Json>): SchemaChecker
}

package org.openfsp.validator.run

import org.openfsp.validator.engine.spi.SchemaChecker
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonObject
import org.openfsp.validator.json.string

object RequiredMembers : SchemaCheckers {
    private val required = listOf("api_key", "expires_at", "control_endpoint")

    override fun of(schemas: Map<String, Json>): SchemaChecker =
        SchemaChecker { _, instance ->
            val document = instance as? JsonObject ?: return@SchemaChecker listOf("not an object")
            required.filter { document[it] == null }.map { "$it is required" } +
                listOfNotNull("api_key must start with ofsp_test_".takeIf { document["api_key"]?.string?.startsWith("ofsp_test_") == false })
        }
}

package org.openfsp.validator.engine.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.validator.engine.spi.SchemaChecker
import org.openfsp.validator.engine.spi.SchemaCheckers
import org.openfsp.validator.json.Json

@Stub
class KnownSchemaCheckers : SchemaCheckers {
    override fun of(schemas: Map<String, Json>): SchemaChecker =
        SchemaChecker { schema, _ -> if (schema in schemas) emptyList() else throw IllegalArgumentException("unknown schema $schema") }
}

package org.openfsp.validator.shared.json

import com.fasterxml.jackson.annotation.JsonInclude
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.JsonNull
import org.openfsp.validator.report.Report
import org.openfsp.validator.report.ResultEntry
import org.openfsp.validator.suite.TargetKind
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.KotlinModule

object ValidatorJson {
    val documents: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(Report::class.java, OmitNulls::class.java)
            .addMixIn(ResultEntry::class.java, OmitNulls::class.java)
            .build()

    val reports: JsonMapper = documents.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build()

    val suite: SimpleModule =
        SimpleModule("openfsp-suite")
            .addDeserializer(Json::class.java, JsonValues())
            .addDeserializer(TargetKind::class.java, TargetKinds())

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract class OmitNulls

    private class JsonValues : ValueDeserializer<Json>() {
        override fun deserialize(
            parser: JsonParser,
            context: DeserializationContext,
        ): Json = JacksonJsonCodec.toJson(context.readTree(parser) ?: return JsonNull)

        override fun getNullValue(context: DeserializationContext): Json = JsonNull
    }

    private class TargetKinds : ValueDeserializer<TargetKind>() {
        override fun deserialize(
            parser: JsonParser,
            context: DeserializationContext,
        ): TargetKind = TargetKind.of(context.readTree(parser).let(JsonNode::asString))
    }
}

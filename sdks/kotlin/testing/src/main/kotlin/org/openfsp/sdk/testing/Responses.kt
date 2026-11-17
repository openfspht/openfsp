package org.openfsp.sdk.testing

import org.openfsp.protocol.ProblemType
import org.openfsp.sdk.http.TransportResponse
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

object Responses {
    internal val mapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    @JvmStatic
    @JvmOverloads
    fun json(
        status: Int,
        body: Any,
        headers: Map<String, String> = emptyMap(),
    ): TransportResponse {
        val contentType = if (status >= 400) ProblemType.MEDIA_TYPE else "application/json"
        return TransportResponse(status, mapOf("Content-Type" to contentType) + headers, mapper.writeValueAsBytes(body))
    }

    @JvmStatic
    @JvmOverloads
    fun problem(
        status: Int,
        code: String,
        fields: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): TransportResponse =
        json(
            status,
            mapOf("type" to "${ProblemType.BASE_URI}$code", "title" to code, "status" to status, "retryable" to false, "effect" to "none", "request_id" to "req_test") + fields,
            headers,
        )
}

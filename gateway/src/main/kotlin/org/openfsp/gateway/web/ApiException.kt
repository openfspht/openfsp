package org.openfsp.gateway.web

import org.openfsp.protocol.Effect
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.ProblemType

class ApiException(
    val type: ProblemType,
    val detail: String? = null,
    val errors: List<FieldError> = emptyList(),
    val effect: Effect = type.effect,
    val headers: Map<String, String> = emptyMap(),
    val providerDetail: Map<String, Any?>? = null,
) : RuntimeException(detail ?: type.title)

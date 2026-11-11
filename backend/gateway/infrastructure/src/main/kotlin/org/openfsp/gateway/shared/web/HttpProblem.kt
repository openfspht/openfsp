package org.openfsp.gateway.shared.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType

class HttpProblem(
    type: ProblemType,
    detail: String? = null,
    val headers: Map<String, String> = emptyMap(),
) : OpenFspException(type, detail)

package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.FieldErrorCode
import org.springframework.stereotype.Component
import java.net.URI

@Component
class EndpointRequestMapper {
    fun toUrl(request: EndpointRequest): URI = runCatching { URI(request.url.trim()) }.getOrElse { throw OpenFspException.invalid("url", "is not a URL", FieldErrorCode.MALFORMED) }

    fun toResource(kid: String): RotatedKeyResource = RotatedKeyResource(kid)
}

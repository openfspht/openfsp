package org.openfsp.gateway.conformance

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.conformance.api.PrepareConformance
import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.shared.web.BearerTokens
import org.openfsp.gateway.shared.web.HttpProblem
import org.openfsp.protocol.ProblemType
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.security.MessageDigest
import java.time.Instant

data class ConformanceRequest(
    val webhookUrl: URI? = null,
)

data class ConformanceFixturesResource(
    val apiKey: String,
    val expiresAt: Instant,
    val inputs: Map<String, String>,
    val controlEndpoint: URI,
)

@RestController
class ConformanceController(
    private val conformance: PrepareConformance,
    private val properties: ConformanceProperties,
    private val bearer: BearerTokens,
    private val resources: ConformanceResourceMapper,
) {
    @PostMapping(PATH)
    fun prepare(
        @RequestBody(required = false) request: ConformanceRequest?,
        http: HttpServletRequest,
    ): ResponseEntity<ConformanceFixturesResource> {
        if (!properties.enabled) throw OpenFspException.notFound()
        properties.accessCode?.let { expected ->
            val presented = bearer.of(http).orEmpty()
            if (!MessageDigest.isEqual(presented.toByteArray(), expected.toByteArray())) {
                throw HttpProblem(ProblemType.UNAUTHENTICATED, headers = mapOf(HttpHeaders.WWW_AUTHENTICATE to "Bearer"))
            }
        }
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .cacheControl(CacheControl.noStore())
            .body(resources.toResource(conformance.prepare(request?.webhookUrl)))
    }

    companion object {
        const val PATH = "/conformance"
    }
}

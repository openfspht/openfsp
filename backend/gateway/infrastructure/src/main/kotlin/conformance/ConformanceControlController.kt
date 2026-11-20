package org.openfsp.gateway.conformance

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.conformance.api.ControlConformance
import org.openfsp.gateway.shared.web.BearerTokens
import org.openfsp.gateway.shared.web.HttpProblem
import org.openfsp.protocol.ProblemType
import org.openfsp.server.api.ControlledPaymentResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ScenarioRequest(
    val scenario: String? = null,
)

data class ResolutionRequest(
    val outcome: String? = null,
    val reason: String? = null,
)

@RestController
@RequestMapping(ConformanceControlController.PATH)
class ConformanceControlController(
    private val control: ControlConformance,
    private val authentication: AuthenticateKey,
    private val properties: ConformanceProperties,
    private val bearer: BearerTokens,
    private val resources: ConformanceResourceMapper,
) {
    @PutMapping("/references/{reference}/scenario")
    fun declare(
        @PathVariable reference: String,
        http: HttpServletRequest,
        @RequestBody request: ScenarioRequest,
    ): ResponseEntity<Void> {
        val caller = callerOf(http)
        control.declare(caller.accountId, caller.principalId, reference, request.scenario)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/references/{reference}")
    fun state(
        @PathVariable reference: String,
        http: HttpServletRequest,
    ): ControlledPaymentResource {
        val caller = callerOf(http)
        return resources.toResource(control.state(caller.accountId, caller.principalId, reference))
    }

    @PostMapping("/payments/{id}/resolve")
    fun resolve(
        @PathVariable id: String,
        http: HttpServletRequest,
        @RequestBody request: ResolutionRequest,
    ): ResponseEntity<Void> {
        val caller = callerOf(http)
        control.resolve(caller.accountId, caller.principalId, id, request.outcome, request.reason)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/payments/{id}/callback")
    fun callback(
        @PathVariable id: String,
        http: HttpServletRequest,
    ): ResponseEntity<Void> {
        val caller = callerOf(http)
        control.callback(caller.accountId, caller.principalId, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("${PaymentScripts.CHECKOUT}{providerReference}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun checkout(
        @PathVariable providerReference: String,
    ): String {
        if (!properties.enabled) throw OpenFspException.notFound()
        return "The payment $providerReference follows a scenario of a conformance run: its outcome is chosen through the control interface."
    }

    private fun callerOf(http: HttpServletRequest): AuthenticatedKey {
        if (!properties.enabled) throw OpenFspException.notFound()
        val presented = bearer.of(http)
        val key = presented?.let(authentication::authenticate) ?: throw HttpProblem(ProblemType.UNAUTHENTICATED, headers = mapOf(HttpHeaders.WWW_AUTHENTICATE to "Bearer"))
        return key
    }

    companion object {
        const val PATH = "/conformance/control"
    }
}

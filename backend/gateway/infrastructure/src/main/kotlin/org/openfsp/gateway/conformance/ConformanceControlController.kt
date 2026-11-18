package org.openfsp.gateway.conformance

import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.conformance.api.ControlConformance
import org.openfsp.gateway.shared.web.HttpProblem
import org.openfsp.protocol.ProblemType
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ScenarioRequest(
    val scenario: String? = null,
)

data class ResolutionRequest(
    val outcome: String? = null,
    val reason: String? = null,
)

data class ControlledPaymentResource(
    val payment: String,
    val settled: Boolean,
    val callbacksSent: Int,
)

@RestController
@RequestMapping(ConformanceControlController.PATH)
class ConformanceControlController(
    private val control: ControlConformance,
    private val authentication: AuthenticateKey,
    private val properties: ConformanceProperties,
) {
    @PutMapping("/references/{reference}/scenario")
    fun declare(
        @PathVariable reference: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: ScenarioRequest,
    ): ResponseEntity<Void> {
        control.declare(principalOf(authorization), reference, request.scenario)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/references/{reference}")
    fun state(
        @PathVariable reference: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ControlledPaymentResource = control.state(principalOf(authorization), reference).let { ControlledPaymentResource(it.paymentId, it.settled, it.callbacksSent) }

    @PostMapping("/payments/{id}/resolve")
    fun resolve(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: ResolutionRequest,
    ): ResponseEntity<Void> {
        control.resolve(principalOf(authorization), id, request.outcome, request.reason)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/payments/{id}/callback")
    fun callback(
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<Void> {
        control.callback(principalOf(authorization), id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("${PaymentScripts.CHECKOUT}{providerReference}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun checkout(
        @PathVariable providerReference: String,
    ): String {
        if (!properties.enabled) throw OpenFspException.notFound()
        return "The payment $providerReference follows a scenario of a conformance run: its outcome is chosen through the control interface."
    }

    private fun principalOf(authorization: String?): String {
        if (!properties.enabled) throw OpenFspException.notFound()
        val presented = authorization?.takeIf { it.startsWith(BEARER) }?.removePrefix(BEARER)
        val key = presented?.let(authentication::authenticate) ?: throw HttpProblem(ProblemType.UNAUTHENTICATED, headers = mapOf(HttpHeaders.WWW_AUTHENTICATE to "Bearer"))
        return key.principalId
    }

    companion object {
        const val PATH = "/conformance/control"
        private const val BEARER = "Bearer "
    }
}

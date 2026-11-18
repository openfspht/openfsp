package org.openfsp.gateway.admin.session

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.audit.api.AuditAdminSignIns
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.ratelimits.RateLimits
import org.openfsp.gateway.shared.security.AdminTokenHashing
import org.openfsp.gateway.shared.web.RequestIds
import org.openfsp.protocol.ProblemType
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SignInRequest(
    val token: String,
)

@RestController
@RequestMapping("/admin/session")
class AdminSessionController(
    private val properties: GatewayProperties,
    private val hashing: AdminTokenHashing,
    private val sessions: AdminSessions,
    private val limits: RateLimits,
    private val audit: AuditAdminSignIns,
    private val gate: AdminSignInGate,
) {
    @PostMapping
    fun signIn(
        @RequestBody request: SignInRequest,
        http: HttpServletRequest,
    ): ResponseEntity<Void> {
        val hash = properties.adminToken ?: throw OpenFspException.notFound()
        limits.adminSignIn(http.remoteAddr)
        val accepted = gate.verify { hashing.matches(request.token, hash) }
        audit.signIn(accepted, RequestIds.of(http))
        if (!accepted) throw OpenFspException(ProblemType.UNAUTHENTICATED)
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.open().toString()).build()
    }

    @GetMapping
    fun current(): ResponseEntity<Void> = ResponseEntity.noContent().build()

    @DeleteMapping
    fun signOut(
        @CookieValue(AdminSessions.COOKIE, required = false) session: String?,
    ): ResponseEntity<Void> = ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.close(session).toString()).build()
}

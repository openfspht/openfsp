package org.openfsp.gateway.admin

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.security.RateLimits
import org.openfsp.gateway.security.admin.AdminSessions
import org.openfsp.gateway.security.admin.AdminTokenHashing
import org.openfsp.gateway.web.RequestIds
import org.openfsp.protocol.ProblemType
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
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
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
) {
    @PostMapping
    fun signIn(
        @RequestBody request: SignInRequest,
        http: HttpServletRequest,
    ): ResponseEntity<Void> {
        val hash = properties.adminToken ?: throw OpenFspException.notFound()
        limits.adminSignIn(http.remoteAddr)
        val accepted = hashing.matches(request.token, hash)
        record(if (accepted) AuditKind.ADMIN_SIGN_IN else AuditKind.ADMIN_SIGN_IN_FAILURE, http, if (accepted) "204" else "401")
        if (!accepted) throw OpenFspException(ProblemType.UNAUTHENTICATED)
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.open().toString()).build()
    }

    @GetMapping
    fun current(): ResponseEntity<Void> = ResponseEntity.noContent().build()

    @DeleteMapping
    fun signOut(): ResponseEntity<Void> = ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.close().toString()).build()

    private fun record(
        kind: AuditKind,
        http: HttpServletRequest,
        result: String,
    ) = audit.record(
        AuditRecord(
            at = calendar.now(),
            kind = kind,
            principalId = null,
            lookupPrefix = null,
            operation = "POST /admin/session",
            requestId = RequestIds.of(http),
            result = result,
        ),
    )
}

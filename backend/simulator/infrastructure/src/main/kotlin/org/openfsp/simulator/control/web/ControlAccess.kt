package org.openfsp.simulator.control.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.dialects.openfsp.OpenFspPrincipals
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.openfsp.simulator.shared.web.ControlProblem
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class ControlAccess(
    private val principals: OpenFspPrincipals,
    private val properties: SimulatorProperties,
) {
    fun scopeOf(request: HttpServletRequest): ControlScope {
        val authorization = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authorization == null) return if (isLoopback(request.remoteAddr)) ControlScope.Instance else throw unauthenticated()
        val caller =
            properties.operators
                .filterValues { it.dialect == Dialect.OPENFSP }
                .keys
                .asSequence()
                .map { principals.callerOf(it, authorization) }
                .firstOrNull { it.authenticated } ?: throw unauthenticated()
        if (caller.principal != Principal.PRIMARY) throw ControlProblem(HttpStatus.FORBIDDEN, "This key cannot use the control interface")
        return ControlScope.MerchantAccount(caller.account)
    }

    private fun isLoopback(address: String): Boolean = runCatching { InetAddress.ofLiteral(address).isLoopbackAddress }.getOrDefault(false)

    private fun unauthenticated() = ControlProblem(HttpStatus.UNAUTHORIZED, "Present the key of a simulated merchant account", mapOf(HttpHeaders.WWW_AUTHENTICATE to BEARER))

    private companion object {
        const val BEARER = "Bearer"
    }
}

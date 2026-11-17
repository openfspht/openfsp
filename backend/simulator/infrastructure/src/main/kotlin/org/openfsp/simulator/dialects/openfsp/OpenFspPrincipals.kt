package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.dialects.constantTimeEquals
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component

@Component
class OpenFspPrincipals(
    private val properties: SimulatorProperties,
) {
    fun callerOf(
        operator: String,
        authorization: String?,
    ): Caller {
        val settings = properties.operator(operator)
        val principal = KEYS.entries.firstOrNull { (name, _) -> settings.credentialOrNull(name)?.let { constantTimeEquals("Bearer $it", authorization) } == true }?.value
        return Caller(authenticated = principal != null, credential = authorization.orEmpty(), principal = principal ?: Principal.PRIMARY)
    }

    private companion object {
        val KEYS = mapOf("api-key" to Principal.PRIMARY, "second-principal-api-key" to Principal.SECONDARY)
    }
}

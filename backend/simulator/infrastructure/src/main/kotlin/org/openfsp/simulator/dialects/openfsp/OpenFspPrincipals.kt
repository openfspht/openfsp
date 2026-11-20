package org.openfsp.simulator.dialects.openfsp

import org.openfsp.server.Digests
import org.openfsp.simulator.emission.api.OpenRuns
import org.openfsp.simulator.operator.Caller
import org.openfsp.simulator.operator.Principal
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.HexFormat

@Component
class OpenFspPrincipals(
    private val properties: SimulatorProperties,
    private val runs: OpenRuns,
    private val digests: Digests,
) {
    private val random = SecureRandom()

    fun callerOf(
        operator: String,
        authorization: String?,
    ): Caller {
        val settings = properties.operator(operator)
        val principal = KEYS.entries.firstOrNull { (name, _) -> settings.credentialOrNull(name)?.let { digests.secretEquals("Bearer $it", authorization) } == true }?.value
        if (principal != null) return Caller.ofPublishedAccount(authenticated = true, credential = authorization.orEmpty(), principal = principal)
        return authorization?.takeIf { it.startsWith(BEARER) }?.let { runs.authenticate(operator, it.removePrefix(BEARER)) }?.copy(credential = authorization)
            ?: Caller.ofPublishedAccount(authenticated = false, credential = authorization.orEmpty())
    }

    fun issueKey(): String = KEY_PREFIX + HexFormat.of().formatHex(ByteArray(KEY_BYTES).also(random::nextBytes))

    private companion object {
        const val BEARER = "Bearer "
        const val KEY_PREFIX = "ofsp_test_"
        const val KEY_BYTES = 24
        val KEYS = mapOf("api-key" to Principal.PRIMARY, "second-principal-api-key" to Principal.SECONDARY)
    }
}

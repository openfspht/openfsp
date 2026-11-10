package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.hexEquals
import org.openfsp.gateway.providers.hmacSha256Hex

class SimulatedCallbacks(
    private val json: ProviderJson,
    private val secret: String?,
) {
    fun signed(request: CallbackRequest): SimulatedCallback? {
        val key = secret ?: return null
        val presented = request.headers(SIGNATURE_HEADER)?.removePrefix(SIGNATURE_PREFIX) ?: return null
        if (!hexEquals(hmacSha256Hex(key, request.body), presented)) return null
        return unsigned(request)
    }

    fun unsigned(request: CallbackRequest): SimulatedCallback? = json.readOrNull(request.body, SimulatedCallback::class.java)

    private companion object {
        const val SIGNATURE_HEADER = "X-Mock-Signature"
        const val SIGNATURE_PREFIX = "sha256="
    }
}

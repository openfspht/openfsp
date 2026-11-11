package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction

class MonCashErrors(
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
) {
    fun rejected(answer: ProviderAnswer): ProviderFailure.Rejected {
        val error = json.readOrNull(answer.body, MonCashError::class.java)
        val detail = mapOf("code" to (error?.error ?: answer.status.toString()), "message" to error?.message)
        return ProviderFailure.Rejected("MonCash answered ${answer.status}", redaction.redact(detail))
    }
}

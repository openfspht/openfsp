package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.gateway.providers.moncash.MonCashProvider.Companion.PROVIDER

class MonCashErrors(
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
    private val calendar: BusinessCalendar,
) {
    fun failure(answer: ProviderAnswer): ProviderFailure = answer.rateLimited(PROVIDER, calendar.now()) ?: rejected(answer)

    fun isNoPayment(answer: ProviderAnswer): Boolean = answer.status == NOT_FOUND && json.readOrNull(answer.body, MonCashError::class.java)?.error == NOT_FOUND_ERROR

    private fun rejected(answer: ProviderAnswer): ProviderFailure.Rejected {
        val error = json.readOrNull(answer.body, MonCashError::class.java)
        return answer.rejected(PROVIDER, error?.error, error?.message, redaction)
    }

    private companion object {
        const val NOT_FOUND = 404
        const val NOT_FOUND_ERROR = "Not Found"
    }
}

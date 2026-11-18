package org.openfsp.gateway.payment.api

interface NotifyPayment {
    fun onLookupNotification(
        providerId: String,
        account: String?,
        parameters: Map<String, String>,
    ): CallbackOutcome?
}

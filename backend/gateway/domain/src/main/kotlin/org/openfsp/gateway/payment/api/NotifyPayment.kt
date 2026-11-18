package org.openfsp.gateway.payment.api

interface NotifyPayment {
    fun onLookupNotification(
        providerId: String,
        parameters: Map<String, String>,
    ): CallbackOutcome?
}

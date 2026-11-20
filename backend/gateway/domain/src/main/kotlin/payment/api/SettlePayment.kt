package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderReport
import java.net.URI

interface SettlePayment {
    fun apply(
        paymentId: String,
        report: ProviderReport,
        source: TransitionSource,
    ): Payment

    fun onSignedCallback(
        providerId: String,
        request: CallbackRequest,
    ): CallbackOutcome

    fun onPaymentUrlCallback(
        providerId: String,
        token: String,
        request: CallbackRequest,
    ): Payment?

    fun returnTargetOf(
        providerId: String,
        token: String,
    ): URI?
}

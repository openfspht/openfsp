package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.CreatePaymentCommand
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderReport
import java.net.URI

interface CreatePayment {
    fun create(command: CreatePaymentCommand): Payment
}

interface ReadPayments {
    fun find(paymentId: String): Payment

    fun byReference(reference: String): Payment?
}

interface ReturnPayer {
    fun returnTarget(
        providerId: String,
        parameters: Map<String, String>,
    ): URI?
}

interface SynchronizePayment {
    fun synchronize(paymentId: String): Payment
}

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
}

interface AttestPayment {
    fun attest(
        paymentId: String,
        attestation: Attestation,
    ): Payment
}

interface ReconcilePendingPayments {
    fun reconcile(batch: Int): Int
}

sealed interface CallbackOutcome {
    data class Applied(
        val payment: Payment,
    ) : CallbackOutcome

    data object UnknownProvider : CallbackOutcome

    data object Unverified : CallbackOutcome

    data object UnknownPayment : CallbackOutcome
}

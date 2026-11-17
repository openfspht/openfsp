package org.openfsp.gateway.payment.api

import org.openfsp.gateway.payment.Payment

sealed interface CallbackOutcome {
    data class Applied(
        val payment: Payment,
    ) : CallbackOutcome

    data object UnknownProvider : CallbackOutcome

    data object Unverified : CallbackOutcome

    data object UnknownPayment : CallbackOutcome
}

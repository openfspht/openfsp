package org.openfsp.gateway.provider

sealed interface VerifiedCallback {
    data class Reported(
        val report: ProviderReport,
    ) : VerifiedCallback

    data class Unreported(
        val eventType: String,
    ) : VerifiedCallback
}

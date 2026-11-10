package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import java.time.LocalDate

interface ProviderOperations {
    val id: String
    val descriptor: ProviderDescriptor
    val limits: AmountLimits

    fun create(request: ProviderPaymentRequest): ProviderPayment

    fun lookup(orderId: String): ProviderReport

    fun verifiedCallback(
        headers: (String) -> String?,
        body: ByteArray,
    ): ProviderReport?

    fun unsignedCallback(body: ByteArray): ProviderReport?

    fun statement(day: LocalDate): ProviderStatement
}

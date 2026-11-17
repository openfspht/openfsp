package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
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

    fun verifiedCallback(request: CallbackRequest): ProviderReport?

    fun unsignedCallback(request: CallbackRequest): ProviderReport?

    fun returnedOrder(parameters: Map<String, String>): String? = parameters[ORDER_ID]

    fun statement(day: LocalDate): ProviderStatement

    companion object {
        const val ORDER_ID = "order_id"
    }
}

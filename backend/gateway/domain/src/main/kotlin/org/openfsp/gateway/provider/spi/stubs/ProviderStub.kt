package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.time.LocalDate

@Stub
class ProviderStub(
    override val id: String = "mock_alpha",
    override val descriptor: ProviderDescriptor =
        ProviderDescriptor(
            displayName = "Stub",
            capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
            nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
            payerRequired = false,
        ),
    override val limits: AmountLimits = AmountLimits(),
) : ProviderOperations {
    val requests = mutableListOf<ProviderPaymentRequest>()
    val statuses = mutableMapOf<String, PaymentStatus>()
    var failure: ProviderFailure? = null
    var callbackReport: ProviderReport? = null
    val callbacks = mutableListOf<CallbackRequest>()

    override fun create(request: ProviderPaymentRequest): ProviderPayment {
        requests += request
        failure?.let { throw it }
        statuses.putIfAbsent(request.orderId, PaymentStatus.PENDING)
        return ProviderPayment(report(request.orderId, request), NextAction.payerApproval, null)
    }

    override fun lookup(orderId: String): ProviderReport {
        failure?.let { throw it }
        if (orderId !in statuses) throw ProviderFailure.Rejected("The provider knows no order $orderId", withoutEffect = true)
        return report(orderId, requests.last { it.orderId == orderId })
    }

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? = callbackReport.also { callbacks += request }

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    override fun returnedOrder(parameters: Map<String, String>): String? = parameters["order_id"] ?: error("unreadable return")

    override fun statement(day: LocalDate): ProviderStatement = ProviderStatement(day, closed = false, lines = emptyList())

    private fun report(
        orderId: String,
        request: ProviderPaymentRequest,
    ) = ProviderReport(orderId, "STUB-$orderId", statuses.getValue(orderId), request.amount)
}

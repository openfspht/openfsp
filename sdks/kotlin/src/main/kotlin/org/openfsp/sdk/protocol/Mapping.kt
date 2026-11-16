package org.openfsp.sdk.protocol

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.PayerResource
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.sdk.InvalidRequestException
import org.openfsp.sdk.model.AmountLimits
import org.openfsp.sdk.model.Fee
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.model.NextAction
import org.openfsp.sdk.model.Payment
import org.openfsp.sdk.model.PaymentEvent
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.model.Provider
import org.openfsp.protocol.Money as WireMoney
import org.openfsp.protocol.NextAction as WireNextAction
import org.openfsp.protocol.api.PaymentEvent as WireEvent
import org.openfsp.protocol.api.PaymentRequest as WirePaymentRequest

internal object Mapping {
    private val PHONE_NUMBER = Regex("^\\+[1-9]\\d{1,14}$")

    fun toWire(request: PaymentRequest): WirePaymentRequest {
        if (request.reference.isBlank()) throw InvalidRequestException("reference must not be empty")
        if (request.provider.isBlank()) throw InvalidRequestException("provider must not be empty")
        if (!request.amount.isPositive) throw InvalidRequestException("amount must be positive")
        if (request.payerPhoneNumber != null && !PHONE_NUMBER.matches(request.payerPhoneNumber)) {
            throw InvalidRequestException("payerPhoneNumber must be in E.164 format, for example +50934567890")
        }
        return WirePaymentRequest(
            reference = request.reference,
            amount = WireMoney.htg(request.amount.centimes),
            provider = request.provider,
            payer = request.payerPhoneNumber?.let(::PayerResource),
            description = request.description,
            returnUrl = request.returnUrl,
            expiresAt = request.expiresAt,
            locale = request.locale,
            metadata = request.metadata.takeIf { it.isNotEmpty() },
            callbackUrl = request.callbackUrl,
        )
    }

    fun toPayment(wire: PaymentResource): Payment =
        Payment(
            id = wire.id,
            reference = wire.reference,
            status = PaymentStatus.fromWire(wire.status) ?: throw Problems.unexpected(200, "unknown payment status ${wire.status}"),
            amount = toMoney(wire.amount),
            provider = wire.provider,
            payerPhoneNumber = wire.payer?.phoneNumber,
            description = wire.description,
            nextAction = toNextAction(wire.nextAction),
            createdAt = wire.createdAt,
            updatedAt = wire.updatedAt,
            expiresAt = wire.expiresAt,
            completedAt = wire.completedAt,
            failureReason = wire.failureReason?.let(FailureReason::fromWire),
            fee = wire.fee?.let { Fee(toMoney(it.amount), if (it.bearer == org.openfsp.protocol.api.Fee.PAYER) Fee.Bearer.PAYER else Fee.Bearer.MERCHANT) },
            metadata = wire.metadata.orEmpty(),
        )

    fun toEvent(wire: WireEvent): PaymentEvent =
        PaymentEvent(
            id = wire.id,
            type = wire.type,
            sequence = wire.sequence,
            createdAt = wire.createdAt,
            previousStatus = wire.previousStatus?.let(PaymentStatus::fromWire),
            payment = toPayment(wire.data),
        )

    fun toProviders(wire: CapabilitiesResource): List<Provider> = wire.providers.map(::toProvider)

    private fun toProvider(wire: ProviderCapabilitiesResource): Provider =
        Provider(
            id = wire.id,
            displayName = wire.displayName,
            logoUrl = wire.logoUrl,
            capabilities = wire.capabilities,
            nextActions = wire.payments.nextActions,
            payerRequired = wire.payments.payerRequired,
            returnUrlRequired = wire.payments.returnUrlRequired,
            expiryGuaranteed = wire.payments.expiryGuaranteed,
            limits =
                AmountLimits(
                    min = wire.payments.limits?.minAmount?.let(::toMoney),
                    max = wire.payments.limits?.maxAmount?.let(::toMoney),
                    step = wire.payments.limits?.amountStep?.let(::toMoney),
                ),
            locales = wire.payments.locales,
        )

    private fun toMoney(wire: WireMoney): Money = Money.centimes(wire.amount)

    private fun toNextAction(wire: WireNextAction?): NextAction =
        when (wire?.type) {
            WireNextAction.Type.REDIRECT -> NextAction.Redirect(requireNotNull(wire.redirectUrl), wire.expiresAt)
            WireNextAction.Type.PAYER_APPROVAL -> NextAction.PayerApproval
            WireNextAction.Type.NONE, null -> NextAction.None
        }
}

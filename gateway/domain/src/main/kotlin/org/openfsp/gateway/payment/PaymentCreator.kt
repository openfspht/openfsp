package org.openfsp.gateway.payment

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.payment.api.CreateAPayment
import org.openfsp.gateway.payment.api.SettleAPayment
import org.openfsp.gateway.payment.spi.PaymentEvents
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.ResourceIds
import java.net.URI

@DomainService
class PaymentCreator(
    private val providers: Providers,
    private val payments: Payments,
    private val journal: PaymentJournal,
    private val events: PaymentEvents,
    private val settlement: SettleAPayment,
    private val transactions: Transactions,
    private val signals: OperatorSignals,
    private val orderIds: OrderIdDerivation,
    private val tokens: CallbackTokens,
    private val calendar: BusinessCalendar,
    private val callbackBase: CallbackBase,
) : CreateAPayment {
    override fun create(command: CreatePaymentCommand): Payment {
        val provider = providers[command.provider] ?: throw OpenFspException.invalid("provider", "is not served by this gateway")
        if (provider.descriptor.payerRequired && command.payerPhoneNumber == null) throw OpenFspException.missing("payer", "is required by ${provider.id}")
        if (provider.descriptor.returnUrlRequired && command.returnUrl == null) {
            throw OpenFspException.missing("return_url", "is required by ${provider.id}")
        }
        provider.limits.check(command.amount)
        if (payments.existsWithReference(command.reference)) throw OpenFspException(ProblemType.REFERENCE_CONFLICT)

        val orderId = orderIds.derive(command.reference)
        val token = if (provider.descriptor.hasPaymentUrl) tokens.generate() else null
        val request =
            ProviderPaymentRequest(
                orderId = orderId,
                amount = command.amount,
                payerPhoneNumber = command.payerPhoneNumber,
                description = command.description,
                callbackUrl = callbackBase.forProvider(provider.id, token),
            )
        val opened = open(command, provider, orderId, token?.let(tokens::digest))
        return if (provider.descriptor.idempotentCreation) createAt(provider, request, opened) else createProvisionally(provider, request, opened)
    }

    private fun createAt(
        provider: ProviderOperations,
        request: ProviderPaymentRequest,
        opened: Payment,
    ): Payment {
        val created = call { provider.create(request) }.also { checkEcho(it, opened) }
        val payment = record(opened.adopt(created, calendar.now()), announce = true)
        return settleIfTerminal(payment, created)
    }

    private fun createProvisionally(
        provider: ProviderOperations,
        request: ProviderPaymentRequest,
        opened: Payment,
    ): Payment {
        val provisional = record(opened, announce = false)
        val created =
            try {
                provider.create(request).also { checkEcho(it, opened) }
            } catch (failure: ProviderFailure.Unavailable) {
                transactions.inTransaction {
                    journal.forget(provisional.id)
                    payments.delete(provisional.id)
                }
                throw failure.toOpenFsp()
            } catch (failure: RuntimeException) {
                transactions.inTransaction {
                    journal.record(IndeterminateOperation(provisional.id, CREATE, failure.message ?: failure.javaClass.simpleName, calendar.now()))
                    events.publish(PaymentTransitioned(provisional.id, null, PaymentStatus.PENDING))
                }
                signals.signal(provisional.id, "creation at ${provider.id} has an unknown outcome: the payment stays pending and is not resent")
                throw when (failure) {
                    is ProviderFailure -> failure.toOpenFsp()
                    is OpenFspException -> failure
                    else -> OpenFspException(ProblemType.INTERNAL_ERROR)
                }
            }
        val payment =
            transactions.inTransaction {
                val locked = payments.lock(provisional.id) ?: error("provisional payment ${provisional.id} vanished")
                payments.save(locked.adopt(created, calendar.now())).also { events.publish(PaymentTransitioned(it.id, null, PaymentStatus.PENDING)) }
            }
        return settleIfTerminal(payment, created)
    }

    private fun open(
        command: CreatePaymentCommand,
        provider: ProviderOperations,
        orderId: String,
        tokenDigest: String?,
    ): Payment =
        Payment(
            id = ResourceIds.generate("pay"),
            reference = command.reference,
            amount = command.amount,
            provider = provider.id,
            providerOrderId = orderId,
            payerPhoneNumber = command.payerPhoneNumber,
            description = command.description,
            returnUrl = command.returnUrl,
            nextAction = provider.descriptor.staticNextAction,
            metadata = command.metadata,
            createdAt = calendar.now(),
            callbackTokenDigest = tokenDigest,
        )

    private fun record(
        payment: Payment,
        announce: Boolean,
    ): Payment =
        try {
            transactions.inTransaction {
                payments.save(payment).also {
                    journal.record(Transition(it.id, null, PaymentStatus.PENDING, TransitionSource.CREATION, it.createdAt))
                    if (announce) events.publish(PaymentTransitioned(it.id, null, PaymentStatus.PENDING))
                }
            }
        } catch (_: DuplicateReference) {
            throw OpenFspException(ProblemType.REFERENCE_CONFLICT)
        }

    private fun settleIfTerminal(
        payment: Payment,
        created: ProviderPayment,
    ): Payment = if (created.report.status.isTerminal) settlement.apply(payment.id, created.report, TransitionSource.CREATION) else payment

    private fun checkEcho(
        created: ProviderPayment,
        opened: Payment,
    ) {
        if (created.report.amount != opened.amount) throw ProviderFailure.Rejected("The provider echoed a different amount")
    }

    private fun <T> call(operation: () -> T): T =
        try {
            operation()
        } catch (failure: ProviderFailure) {
            throw failure.toOpenFsp()
        }

    private companion object {
        const val CREATE = "create"
    }
}

class DuplicateReference : RuntimeException("reference already used")

fun interface CallbackBase {
    fun forProvider(
        providerId: String,
        token: String?,
    ): URI
}

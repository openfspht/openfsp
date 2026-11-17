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
import java.time.Duration

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
        val existing = payments.withReference(command.reference)
        if (existing != null) return resume(provider, command, existing)

        val orderId = orderIds.derive(command.reference)
        val opened = open(command, provider, orderId)
        val token = callbackToken(provider, opened)
        val provisional =
            record(opened.copy(callbackTokenDigest = token?.let(tokens::digest)))
                ?: return resume(provider, command, payments.withReference(command.reference) ?: throw OpenFspException(ProblemType.REFERENCE_CONFLICT))
        return createAt(provider, request(command, provider, orderId, token), provisional, firstAttempt = true)
    }

    private fun resume(
        provider: ProviderOperations,
        command: CreatePaymentCommand,
        existing: Payment,
    ): Payment {
        val resent = command.request != null && existing.creationRequest == command.request
        if (!resent && !(existing.awaitsCreation && existing.isRequestedBy(command))) throw OpenFspException(ProblemType.REFERENCE_CONFLICT)
        if (existing.isCreationInFlight && !existing.isCreationAbandonedAt(calendar.now())) throw inProgress()
        if (!existing.awaitsCreation) return existing
        val looked = lookUp(provider, existing)
        if (!looked.awaitsCreation) return looked
        if (!provider.descriptor.idempotentCreation) {
            throw OpenFspException(ProblemType.PROVIDER_ERROR, "The creation at ${provider.id} has an unknown outcome and is not resent")
        }
        val token = callbackToken(provider, looked)
        val provisional =
            if (token == null || tokens.matches(token, looked.callbackTokenDigest)) {
                looked
            } else {
                transactions.inTransaction {
                    val locked = payments.lock(looked.id) ?: throw OpenFspException.notFound()
                    payments.save(locked.copy(callbackTokenDigest = tokens.digest(token)))
                }
            }
        return createAt(provider, request(command, provider, existing.providerOrderId, token), provisional, firstAttempt = false)
    }

    private fun callbackToken(
        provider: ProviderOperations,
        payment: Payment,
    ): String? = if (provider.descriptor.hasPaymentUrl) tokens.forPayment(payment.id) else null

    private fun lookUp(
        provider: ProviderOperations,
        payment: Payment,
    ): Payment {
        if (!provider.descriptor.hasLookup) return payment
        val report =
            try {
                provider.lookup(payment.providerOrderId)
            } catch (_: ProviderFailure.Rejected) {
                return payment
            } catch (failure: ProviderFailure) {
                throw failure.toOpenFsp()
            }
        return settlement.apply(payment.id, report, TransitionSource.LOOKUP)
    }

    private fun createAt(
        provider: ProviderOperations,
        request: ProviderPaymentRequest,
        provisional: Payment,
        firstAttempt: Boolean,
    ): Payment {
        val created =
            try {
                provider.create(request).also { checkEcho(it, provisional) }
            } catch (failure: ProviderFailure.InProgress) {
                throw failure.toOpenFsp()
            } catch (failure: ProviderFailure.Unavailable) {
                if (firstAttempt) forget(provisional)
                throw failure.toOpenFsp()
            } catch (failure: ProviderFailure.Rejected) {
                if (failure.withoutEffect && firstAttempt) {
                    forget(provisional)
                    throw failure.toOpenFsp()
                }
                keepPending(provider, provisional, failure)
                throw OpenFspException(ProblemType.PROVIDER_ERROR, failure.problemDetail)
            } catch (failure: RuntimeException) {
                keepPending(provider, provisional, failure)
                throw when (failure) {
                    is ProviderFailure -> failure.toOpenFsp()
                    is OpenFspException -> failure
                    else -> OpenFspException(ProblemType.INTERNAL_ERROR)
                }
            }
        val payment =
            transactions.inTransaction {
                val locked = payments.lock(provisional.id) ?: error("provisional payment ${provisional.id} vanished")
                if (locked.isTerminal) {
                    locked
                } else {
                    payments.save(locked.adopt(created, calendar.now())).also { if (locked.isCreationInFlight) announce(it) }
                }
            }
        return if (payment.isTerminal) payment else settleIfTerminal(payment, created)
    }

    private fun forget(provisional: Payment) =
        transactions.inTransaction {
            val locked = payments.lock(provisional.id)
            if (locked != null && locked.isCreationInFlight) {
                journal.forget(locked.id)
                payments.delete(locked.id)
            }
        }

    private fun keepPending(
        provider: ProviderOperations,
        provisional: Payment,
        failure: RuntimeException,
    ) {
        transactions.inTransaction {
            journal.record(IndeterminateOperation(provisional.id, CREATE, failure.message ?: failure.javaClass.simpleName, calendar.now()))
            val locked = payments.lock(provisional.id)
            if (locked != null && locked.isCreationInFlight) announce(payments.save(locked.withUnknownCreationOutcome()))
        }
        val next = if (provider.descriptor.idempotentCreation) "its order is looked up before any new creation" else "it is not resent"
        signals.signal(provisional.id, "creation at ${provider.id} has an unknown outcome: the payment stays pending and $next")
    }

    private fun announce(payment: Payment) = events.publish(PaymentTransitioned(payment.id, null, PaymentStatus.PENDING))

    private fun request(
        command: CreatePaymentCommand,
        provider: ProviderOperations,
        orderId: String,
        token: String?,
    ) = ProviderPaymentRequest(
        orderId = orderId,
        amount = command.amount,
        payerPhoneNumber = command.payerPhoneNumber,
        description = command.description,
        callbackUrl = callbackBase.forProvider(provider.id, token),
        locale = command.locale?.takeIf { it in provider.descriptor.locales },
        returnUrl = command.returnUrl,
        expiresAt = command.expiresAt,
    )

    private fun Payment.isRequestedBy(command: CreatePaymentCommand): Boolean =
        provider == command.provider &&
            amount == command.amount &&
            payerPhoneNumber == command.payerPhoneNumber &&
            description == command.description &&
            returnUrl == command.returnUrl &&
            locale == command.locale &&
            metadata == command.metadata &&
            expiresAt == command.expiresAt

    private fun open(
        command: CreatePaymentCommand,
        provider: ProviderOperations,
        orderId: String,
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
            metadata = command.metadata,
            locale = command.locale,
            creationRequest = command.request,
            expiresAt = command.expiresAt,
            createdAt = calendar.now(),
        )

    private fun record(payment: Payment): Payment? =
        try {
            transactions.inTransaction {
                payments.save(payment).also { journal.record(Transition(it.id, null, PaymentStatus.PENDING, TransitionSource.CREATION, it.createdAt)) }
            }
        } catch (_: DuplicateReference) {
            null
        }

    private fun inProgress() = OpenFspException(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, retryAfter = RETRY_AFTER)

    private fun settleIfTerminal(
        payment: Payment,
        created: ProviderPayment,
    ): Payment = if (created.report.status.isTerminal) settlement.apply(payment.id, created.report, TransitionSource.CREATION) else payment

    private fun checkEcho(
        created: ProviderPayment,
        opened: Payment,
    ) {
        if (created.report.amount != null && created.report.amount != opened.amount) throw ProviderFailure.Rejected("The provider echoed a different amount")
    }

    private companion object {
        const val CREATE = "create"
        val RETRY_AFTER: Duration = Duration.ofSeconds(1)
    }
}

class DuplicateReference : RuntimeException("reference already used")

fun interface CallbackBase {
    fun forProvider(
        providerId: String,
        token: String?,
    ): URI
}

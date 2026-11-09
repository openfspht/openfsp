package org.openfsp.gateway.payment

import org.openfsp.gateway.GatewayProperties
import org.openfsp.gateway.provider.ProviderAdapter
import org.openfsp.gateway.provider.ProviderException
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.Providers
import org.openfsp.gateway.provider.Source
import org.openfsp.gateway.web.ApiException
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.OrderIdDerivation
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.ResourceIds
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

data class PaymentTransitioned(
    val paymentId: String,
    val previous: PaymentStatus?,
    val current: PaymentStatus,
)

@Service
class Payments(
    private val repository: PaymentRepository,
    private val providers: Providers,
    private val derivation: OrderIdDerivation,
    private val properties: GatewayProperties,
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
    private val clock: Clock,
    private val transactions: TransactionTemplate,
    private val events: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun create(command: CreatePayment): PaymentEntity {
        val adapter =
            providers[command.provider]
                ?: throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("provider", "is not served by this gateway")))
        if (adapter.requiresPayer && command.payerPhoneNumber == null) {
            throw ApiException(ProblemType.MISSING_FIELD, errors = listOf(FieldError("payer", "is required by ${adapter.id}")))
        }
        if (repository.existsByReference(command.reference)) throw ApiException(ProblemType.REFERENCE_CONFLICT)

        val orderId = derivation.derive(command.reference)
        val created =
            provider(adapter) {
                create(
                    ProviderPaymentRequest(
                        orderId = orderId,
                        amount = command.amount,
                        payerPhoneNumber = command.payerPhoneNumber,
                        description = command.description,
                        callbackUrl = properties.publicBaseUrl.resolve("/callbacks/${adapter.id}"),
                    ),
                )
            }
        if (created.report.amount != command.amount) throw ApiException(ProblemType.PROVIDER_ERROR, "The provider echoed a different amount")

        val now = now()
        val payment =
            PaymentEntity(
                id = ResourceIds.generate("pay", clock),
                reference = command.reference,
                status = PaymentStatus.PENDING.wire,
                amount = command.amount.amount,
                currency = command.amount.currency.code,
                provider = adapter.id,
                providerReference = created.report.providerReference,
                providerOrderId = orderId,
                payerPhoneNumber = command.payerPhoneNumber,
                description = command.description,
                returnUrl = command.returnUrl?.toString(),
                nextAction = json.writeValueAsString(created.nextAction),
                metadataJson = command.metadata?.let(json::writeValueAsString),
                expiresAt = created.expiresAt?.truncatedTo(ChronoUnit.MILLIS),
                completedAt = null,
                failureReason = null,
                failureDetail = null,
                createdAt = now,
                updatedAt = now,
            )
        val saved =
            try {
                transactions.execute {
                    repository.saveAndFlush(payment).also {
                        recordTransition(it.id, null, PaymentStatus.PENDING, Source.CREATION)
                        events.publishEvent(PaymentTransitioned(it.id, null, PaymentStatus.PENDING))
                    }
                }
            } catch (_: DataIntegrityViolationException) {
                throw ApiException(ProblemType.REFERENCE_CONFLICT)
            }
        if (created.report.status.isTerminal) return apply(saved.id, created.report, Source.CREATION)
        return saved
    }

    fun find(id: String): PaymentEntity = repository.findById(id).orElseThrow { ApiException(ProblemType.NOT_FOUND) }

    fun findByReference(reference: String): PaymentEntity? = repository.findByReference(reference)

    fun synchronize(id: String): PaymentEntity {
        val payment = find(id)
        if (PaymentStatus.fromWire(payment.status)!!.isTerminal) return payment
        val adapter = providers[payment.provider] ?: throw ApiException(ProblemType.PROVIDER_UNAVAILABLE)
        val report = provider(adapter) { lookup(payment.providerOrderId) }
        return apply(payment.id, report, Source.LOOKUP)
    }

    fun onCallback(
        providerId: String,
        report: ProviderReport,
    ): PaymentEntity? {
        val payment = repository.findByProviderAndProviderOrderId(providerId, report.orderId) ?: return null
        return apply(payment.id, report, Source.SIGNED_CALLBACK)
    }

    fun apply(
        paymentId: String,
        report: ProviderReport,
        source: Source,
    ): PaymentEntity =
        transactions.execute {
            val payment = repository.lockById(paymentId)!!
            val current = PaymentStatus.fromWire(payment.status)!!
            when {
                current.isTerminal -> {
                    if (report.status != current && report.status != PaymentStatus.PENDING) recordConflict(payment, report, source)
                }
                report.status == PaymentStatus.PENDING -> {
                    if (payment.providerReference == null && report.providerReference != null) {
                        payment.providerReference = report.providerReference
                        payment.updatedAt = now()
                    }
                }
                report.status == PaymentStatus.SUCCEEDED && report.amount.amount != payment.amount -> recordConflict(payment, report, source)
                else -> transition(payment, current, report, source)
            }
            payment
        }

    private fun transition(
        payment: PaymentEntity,
        current: PaymentStatus,
        report: ProviderReport,
        source: Source,
    ) {
        check(current.canTransitionTo(report.status)) { "$current -> ${report.status}" }
        val now = now()
        payment.status = report.status.wire
        payment.completedAt = now
        payment.updatedAt = now
        payment.nextAction = null
        payment.providerReference = payment.providerReference ?: report.providerReference
        if (report.status == PaymentStatus.FAILED) {
            payment.failureReason = (report.failureReason ?: org.openfsp.protocol.FailureReason.UNSPECIFIED).wire
            payment.failureDetail = report.failureDetail?.let(json::writeValueAsString)
        }
        recordTransition(payment.id, current, report.status, source)
        events.publishEvent(PaymentTransitioned(payment.id, current, report.status))
    }

    private fun recordTransition(
        paymentId: String,
        from: PaymentStatus?,
        to: PaymentStatus,
        source: Source,
    ) {
        jdbc
            .sql("insert into payment_transitions (payment_id, from_status, to_status, source, recorded_at) values (?, ?, ?, ?, ?)")
            .params(listOf(paymentId, from?.wire, to.wire, source.wire, Timestamp.from(now())))
            .update()
    }

    private fun recordConflict(
        payment: PaymentEntity,
        report: ProviderReport,
        source: Source,
    ) {
        log.warn("Conflict on payment {}: {} reported {} for an amount of {}", payment.id, source.wire, report.status.wire, report.amount.amount)
        jdbc
            .sql("insert into payment_conflicts (payment_id, source, reported, recorded_at) values (?, ?, ?, ?)")
            .params(
                payment.id,
                source.wire,
                json.writeValueAsString(mapOf("status" to report.status.wire, "amount" to report.amount.amount)),
                Timestamp.from(now()),
            ).update()
    }

    private fun <T> provider(
        adapter: ProviderAdapter,
        call: ProviderAdapter.() -> T,
    ): T =
        try {
            adapter.call()
        } catch (exception: ProviderException) {
            log.warn("Provider {} call failed: {}", adapter.id, exception.message)
            throw when (exception) {
                is ProviderException.Unavailable -> ApiException(ProblemType.PROVIDER_UNAVAILABLE)
                is ProviderException.Timeout -> ApiException(ProblemType.PROVIDER_TIMEOUT)
                is ProviderException.Error -> ApiException(ProblemType.PROVIDER_ERROR, exception.message, providerDetail = exception.detail)
            }
        }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)
}

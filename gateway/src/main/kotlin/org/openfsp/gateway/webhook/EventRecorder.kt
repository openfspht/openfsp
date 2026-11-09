package org.openfsp.gateway.webhook

import org.openfsp.gateway.GatewayProperties
import org.openfsp.gateway.payment.PaymentRepository
import org.openfsp.gateway.payment.PaymentResource
import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ResourceIds
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

data class Event(
    val id: String,
    val type: String,
    val createdAt: Instant,
    val sequence: Int,
    val resourceType: String,
    val resourceId: String,
    val reference: String,
    val previousStatus: PaymentStatus?,
    val data: PaymentResource,
)

@Component
class EventRecorder(
    private val payments: PaymentRepository,
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
    private val properties: GatewayProperties,
    private val clock: Clock,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun record(transition: PaymentTransitioned) {
        val payment = payments.findById(transition.paymentId).orElseThrow()
        val sequence =
            jdbc
                .sql("select coalesce(max(sequence), 0) + 1 from events where payment_id = ?")
                .param(payment.id)
                .query(Int::class.java)
                .single()
        val type = "payment." + if (transition.previous == null) "created" else transition.current.wire
        val now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        val event =
            Event(
                id = ResourceIds.generate("evt", clock),
                type = type,
                createdAt = now,
                sequence = sequence,
                resourceType = "payment",
                resourceId = payment.id,
                reference = payment.reference,
                previousStatus = transition.previous,
                data = PaymentResource.of(payment, json),
            )
        jdbc
            .sql("insert into events (id, payment_id, type, sequence, previous_status, created_at, body) values (?, ?, ?, ?, ?, ?, ?)")
            .params(listOf(event.id, payment.id, type, sequence, transition.previous?.wire, Timestamp.from(now), json.writeValueAsBytes(event)))
            .update()
        properties.webhooks.endpoints
            .filter { (_, endpoint) -> endpoint.enabled && type in endpoint.events }
            .forEach { (name, endpoint) ->
                jdbc
                    .sql(
                        "insert into webhook_deliveries (event_id, endpoint, url, status, next_attempt_at, updated_at) " +
                            "values (?, ?, ?, 'pending', ?, ?)",
                    ).params(event.id, name, endpoint.url.toString(), Timestamp.from(now), Timestamp.from(now))
                    .update()
            }
    }
}

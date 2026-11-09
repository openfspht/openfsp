package org.openfsp.gateway.admin

import org.openfsp.gateway.payment.PaymentCollection
import org.openfsp.gateway.payment.PaymentRepository
import org.openfsp.gateway.payment.PaymentResource
import org.openfsp.gateway.payment.Payments
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

data class Transition(
    val fromStatus: String?,
    val toStatus: String,
    val source: String,
    val recordedAt: Instant,
)

data class Conflict(
    val source: String,
    val reported: JsonNode,
    val recordedAt: Instant,
)

data class Attempt(
    val attempt: Int,
    val startedAt: Instant,
    val statusCode: Int?,
    val error: String?,
    val durationMs: Int,
)

data class Delivery(
    val endpoint: String,
    val url: String,
    val status: String,
    val attempts: List<Attempt>,
    val nextAttemptAt: Instant?,
)

data class EventView(
    val id: String,
    val type: String,
    val sequence: Int,
    val createdAt: Instant,
    val deliveries: List<Delivery>,
)

data class PaymentDetail(
    val payment: PaymentResource,
    val transitions: List<Transition>,
    val conflicts: List<Conflict>,
    val events: List<EventView>,
)

@RestController
@RequestMapping("/admin")
class AdminController(
    private val payments: Payments,
    private val repository: PaymentRepository,
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
) {
    @GetMapping("/payments")
    fun payments(): PaymentCollection = PaymentCollection(repository.findTop100ByOrderByCreatedAtDesc().map { PaymentResource.of(it, json) })

    @GetMapping("/payments/{id}")
    fun payment(
        @PathVariable id: String,
    ): PaymentDetail {
        val payment = PaymentResource.of(payments.find(id), json)
        val transitions =
            jdbc
                .sql("select from_status, to_status, source, recorded_at from payment_transitions where payment_id = ? order by id")
                .param(id)
                .query { rs, _ -> Transition(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()) }
                .list()
        val conflicts =
            jdbc
                .sql("select source, reported, recorded_at from payment_conflicts where payment_id = ? order by id")
                .param(id)
                .query { rs, _ -> Conflict(rs.getString(1), json.readTree(rs.getString(2)), rs.getTimestamp(3).toInstant()) }
                .list()
        return PaymentDetail(payment, transitions, conflicts, events(id))
    }

    private fun events(paymentId: String): List<EventView> {
        val attempts =
            jdbc
                .sql(
                    "select a.delivery_id, a.attempt, a.started_at, a.status_code, a.error, a.duration_ms from webhook_attempts a " +
                        "join webhook_deliveries d on d.id = a.delivery_id join events e on e.id = d.event_id where e.payment_id = ? order by a.id",
                ).param(paymentId)
                .query { rs, _ ->
                    rs.getLong(1) to
                        Attempt(rs.getInt(2), rs.getTimestamp(3).toInstant(), rs.getObject(4) as Int?, rs.getString(5), rs.getInt(6))
                }.list()
                .groupBy({ it.first }, { it.second })
        val deliveries =
            jdbc
                .sql(
                    "select d.id, d.event_id, d.endpoint, d.url, d.status, d.next_attempt_at from webhook_deliveries d " +
                        "join events e on e.id = d.event_id where e.payment_id = ? order by d.id",
                ).param(paymentId)
                .query { rs, _ ->
                    rs.getString(2) to
                        Delivery(
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            attempts[rs.getLong(1)].orEmpty(),
                            rs.getTimestamp(6)?.toInstant(),
                        )
                }.list()
                .groupBy({ it.first }, { it.second })
        return jdbc
            .sql("select id, type, sequence, created_at from events where payment_id = ? order by sequence")
            .param(paymentId)
            .query { rs, _ -> EventView(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getTimestamp(4).toInstant(), deliveries[rs.getString(1)].orEmpty()) }
            .list()
    }
}

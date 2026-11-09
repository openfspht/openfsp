package org.openfsp.gateway.webhook

import org.openfsp.protocol.webhook.WebhookHeaders
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors

@Component
class WebhookDispatcher(
    private val jdbc: JdbcClient,
    private val keys: SigningKeys,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private data class Due(
        val id: Long,
        val eventId: String,
        val eventType: String,
        val url: String,
        val attempts: Int,
        val body: ByteArray,
    )

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    @Scheduled(fixedDelayString = "\${openfsp.webhooks.poll-interval:500ms}")
    fun dispatch() {
        claimDue().forEach { due -> workers.submit { attempt(due) } }
    }

    private fun claimDue(): List<Due> =
        transactions.execute {
            val due =
                jdbc
                    .sql(
                        """
                        select d.id, d.event_id, e.type, d.url, d.attempts, e.body
                        from webhook_deliveries d join events e on e.id = d.event_id
                        where d.status = 'pending' and d.next_attempt_at <= ?
                        order by d.next_attempt_at
                        limit $BATCH
                        for update of d skip locked
                        """.trimIndent(),
                    ).param(Timestamp.from(clock.instant()))
                    .query { rs, _ -> Due(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBytes(6)) }
                    .list()
            due.forEach { reschedule(it.id, clock.instant().plus(LEASE)) }
            due
        }.orEmpty()

    private fun attempt(due: Due) {
        val attempt = due.attempts + 1
        val started = clock.instant()
        val (status, error, retryAfter) = send(due, attempt)
        val elapsed = Duration.between(started, clock.instant()).toMillis().toInt()
        transactions.executeWithoutResult {
            jdbc
                .sql("insert into webhook_attempts (delivery_id, attempt, started_at, status_code, error, duration_ms) values (?, ?, ?, ?, ?, ?)")
                .params(listOf(due.id, attempt, Timestamp.from(started), status, error, elapsed))
                .update()
            when {
                status != null && status in 200..299 -> finish(due.id, attempt, "succeeded")
                status == GONE -> {
                    log.warn("Endpoint {} answered 410 for event {}: deliveries to it stop", due.url, due.eventId)
                    finish(due.id, attempt, "stopped")
                }
                attempt >= SCHEDULE.size -> {
                    log.warn("Abandoned event {} for {} after {} attempts", due.eventId, due.url, attempt)
                    finish(due.id, attempt, "abandoned")
                }
                else -> {
                    val delay = jittered(SCHEDULE[attempt]).let { base -> retryAfter?.coerceAtMost(MAX_RETRY_AFTER)?.coerceAtLeast(base) ?: base }
                    jdbc
                        .sql("update webhook_deliveries set attempts = ?, next_attempt_at = ?, updated_at = ? where id = ?")
                        .params(attempt, Timestamp.from(clock.instant().plus(delay)), Timestamp.from(clock.instant()), due.id)
                        .update()
                }
            }
        }
    }

    private fun send(
        due: Due,
        attempt: Int,
    ): Triple<Int?, String?, Duration?> =
        try {
            val headers = keys.signer().sign(due.url, due.body, due.eventId, due.eventType)
            val request =
                HttpRequest
                    .newBuilder(URI.create(due.url))
                    .timeout(RESPONSE_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(due.body))
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .header(WebhookHeaders.DELIVERY_ATTEMPT, attempt.toString())
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            val retryAfter =
                if (response.statusCode() == TOO_MANY_REQUESTS || response.statusCode() == UNAVAILABLE) {
                    response.headers().firstValue("Retry-After").map { it.toLongOrNull() }.orElse(null)?.let(Duration::ofSeconds)
                } else {
                    null
                }
            Triple(response.statusCode(), null, retryAfter)
        } catch (exception: Exception) {
            Triple(null, exception.javaClass.simpleName, null)
        }

    private fun finish(
        id: Long,
        attempts: Int,
        status: String,
    ) {
        jdbc
            .sql("update webhook_deliveries set status = ?, attempts = ?, next_attempt_at = null, updated_at = ? where id = ?")
            .params(status, attempts, Timestamp.from(clock.instant()), id)
            .update()
    }

    private fun reschedule(
        id: Long,
        at: Instant,
    ) {
        jdbc.sql("update webhook_deliveries set next_attempt_at = ? where id = ?").params(Timestamp.from(at), id).update()
    }

    private fun jittered(base: Duration): Duration = base.plusMillis((base.toMillis() * (MIN_JITTER + random.nextDouble() * MIN_JITTER)).toLong())

    private companion object {
        const val BATCH = 20
        const val GONE = 410
        const val TOO_MANY_REQUESTS = 429
        const val UNAVAILABLE = 503
        const val MIN_JITTER = 0.2
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(7)
        val LEASE: Duration = Duration.ofMinutes(1)
        val MAX_RETRY_AFTER: Duration = Duration.ofHours(1)
        val SCHEDULE: List<Duration> =
            listOf(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofHours(6),
                Duration.ofHours(24),
            )
    }
}

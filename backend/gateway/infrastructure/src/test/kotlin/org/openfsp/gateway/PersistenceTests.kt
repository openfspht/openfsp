package org.openfsp.gateway

import org.junit.jupiter.api.Test
import org.openfsp.gateway.auth.PresentedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.AuthenticateAKey
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.auth.spi.ApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.idempotency.Claim
import org.openfsp.gateway.idempotency.Idempotency
import org.openfsp.gateway.idempotency.IdempotencyPurgeJob
import org.openfsp.gateway.idempotency.persistence.IdempotencyRecordId
import org.openfsp.gateway.payment.DuplicateReference
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.Transition
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ResourceIds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("development", "test")
class PersistenceTests {
    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var calendar: BusinessCalendar

    @Autowired
    private lateinit var idempotency: Idempotency

    @Autowired
    private lateinit var idempotencyPurge: IdempotencyPurgeJob

    @Autowired
    private lateinit var payments: Payments

    @Autowired
    private lateinit var journal: PaymentJournal

    @Autowired
    private lateinit var transactions: Transactions

    @Autowired
    private lateinit var manageKeys: ManageKeys

    @Autowired
    private lateinit var authentication: AuthenticateAKey

    @Autowired
    private lateinit var apiKeys: ApiKeys

    @Autowired
    private lateinit var deliveries: Deliveries

    private fun keyRow(keyId: String): Map<String, Any?> = jdbc.sql("select digest, revoked_at, last_used_at from api_keys where id = ?").param(keyId).query().singleRow()

    private fun payment(
        reference: String,
        orderId: String,
    ) = Payment(ResourceIds.generate("pay"), reference, Money.htg(1000), "mock_alpha", orderId, nextAction = NextAction.payerApproval, createdAt = calendar.now())

    private fun idempotencyRecord(
        principal: String,
        key: String,
        createdAt: Instant,
    ) {
        jdbc
            .sql("insert into idempotency_records (principal, operation, idempotency_key, fingerprint, response_status, response_body, created_at, claimed_at) values (?, 'payments.create', ?, 'f', 201, '{}'::bytea, ?, ?)")
            .params(principal, key, Timestamp.from(createdAt), Timestamp.from(createdAt))
            .update()
    }

    private fun idempotencyRecords(principal: String): Int = jdbc.sql("select count(*) from idempotency_records where principal = ?").param(principal).query(Int::class.java).single()

    @Test
    fun `a claim no longer purges expired idempotency records`() {
        val principal = "purge-${UUID.randomUUID()}"
        idempotencyRecord(principal, "old", calendar.now().minus(Idempotency.RETENTION).minus(Duration.ofDays(1)))

        assertEquals(Claim.Acquired, idempotency.claim(IdempotencyRecordId(principal, "payments.create", "fresh"), "f"))

        assertEquals(2, idempotencyRecords(principal))
    }

    @Test
    fun `the purge job removes expired idempotency records in bounded batches and keeps recent ones`() {
        val principal = "purge-${UUID.randomUUID()}"
        val expired = calendar.now().minus(Idempotency.RETENTION).minus(Duration.ofDays(1))
        repeat(5) { idempotencyRecord(principal, "old-$it", expired.minusSeconds(it.toLong())) }
        idempotencyRecord(principal, "recent", calendar.now())

        val first = idempotencyPurge.purge(batch = 2, maxBatches = 2)
        val remainingAfterFirst = idempotencyRecords(principal)
        idempotencyPurge.purge(batch = 2, maxBatches = 2)

        assertEquals(4, first)
        assertEquals(2, remainingAfterFirst)
        assertEquals(1, idempotencyRecords(principal))
    }

    @Test
    fun `idempotency records are indexed by creation time`() {
        val indexes = jdbc.sql("select indexdef from pg_indexes where tablename = 'idempotency_records'").query(String::class.java).list()
        assertTrue(indexes.any { it.orEmpty().contains("(created_at)") }, indexes.toString())
    }

    @Test
    fun `only the reference unique constraint reads as a duplicate reference`() {
        val suffix = UUID.randomUUID().toString()
        payments.save(payment("ref-$suffix", "order-$suffix"))

        assertFailsWith<DuplicateReference> { payments.save(payment("ref-$suffix", "other-$suffix")) }
        assertFailsWith<DataIntegrityViolationException> { payments.save(payment("other-$suffix", "order-$suffix")) }
    }

    @Test
    fun `a forgotten payment leaves no journal row behind and can be deleted`() {
        val suffix = UUID.randomUUID().toString()
        val payment = payments.save(payment("forget-$suffix", "forget-$suffix"))
        val now = calendar.now()
        journal.record(Transition(payment.id, null, PaymentStatus.PENDING, TransitionSource.CREATION, now))
        journal.record(PaymentConflict(payment.id, TransitionSource.LOOKUP, PaymentStatus.FAILED, payment.amount, now))
        journal.record(IndeterminateOperation(payment.id, "create", "timeout", now))
        journal.record(payment.id, Attestation("ops@merchant.example", PaymentStatus.SUCCEEDED, "statement line 1"), now)

        transactions.inTransaction {
            journal.forget(payment.id)
            payments.delete(payment.id)
        }

        assertNull(payments.find(payment.id))
    }

    @Test
    fun `a touch arriving after a revocation never restores the key and a second revocation is refused`() {
        val issued = manageKeys.issue("touch-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_READ))
        val beforeRevocation = apiKeys.withPrefix(PresentedKey.parse(issued.secret)!!.lookupPrefix).single { it.id == issued.keyId }

        assertTrue(manageKeys.revoke(issued.keyId))
        apiKeys.touch(beforeRevocation.id, calendar.now())

        val row = keyRow(issued.keyId)
        assertTrue((row["digest"] as ByteArray).all { it == 0.toByte() })
        assertNotNull(row["revoked_at"])
        assertNull(row["last_used_at"])
        assertNull(authentication.authenticate(issued.secret))
        assertFalse(manageKeys.revoke(issued.keyId))
    }

    @Test
    fun `concurrent authentications never undo a concurrent revocation`() {
        val issued = manageKeys.issue("race-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_READ))
        val start = CyclicBarrier(CONCURRENT_READERS + 1)
        val pool = Executors.newFixedThreadPool(CONCURRENT_READERS + 1)
        try {
            val readers = (1..CONCURRENT_READERS).map { pool.submit { start.await(); authentication.authenticate(issued.secret) } }
            val revocation = pool.submit<Boolean> { start.await(); manageKeys.revoke(issued.keyId) }
            readers.forEach { it.get() }
            assertTrue(revocation.get())
        } finally {
            pool.shutdown()
        }

        assertTrue((keyRow(issued.keyId)["digest"] as ByteArray).all { it == 0.toByte() })
        assertNull(authentication.authenticate(issued.secret))
    }

    @Test
    fun `issuing and revoking a key are both recorded with time and scopes in the durable audit`() {
        val issued = manageKeys.issue("audit-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_WRITE))
        manageKeys.revoke(issued.keyId)

        val records = jdbc.sql("select operation, detail, recorded_at from audit_records where result = ? order by id").param(issued.keyId).query().listOfRows()

        assertEquals(listOf("key_issued", "key_revoked"), records.map { it["operation"] })
        assertEquals(listOf("payments:write capabilities:read", "payments:write capabilities:read"), records.map { it["detail"] })
        assertTrue(records.all { it["recorded_at"] != null })
    }

    @Test
    fun `a scheduled webhook retry records the time of the change, not the time of the next attempt`() {
        val suffix = UUID.randomUUID().toString()
        val payment = payments.save(payment("hook-$suffix", "hook-$suffix"))
        val now = calendar.now().truncatedTo(ChronoUnit.SECONDS)
        val next = now.plus(Duration.ofHours(1))
        jdbc
            .sql("insert into events (id, payment_id, type, sequence, created_at, body) values (?, ?, 'payment.created', 1, ?, '{}'::bytea)")
            .params("evt_$suffix", payment.id, Timestamp.from(now))
            .update()
        val deliveryId =
            jdbc
                .sql("insert into webhook_deliveries (event_id, endpoint, url, status, attempts, updated_at) values (?, ?, 'https://hooks.example/a', 'succeeded', 1, ?) returning id")
                .params("evt_$suffix", "endpoint-$suffix", Timestamp.from(now.minusSeconds(60)))
                .query(Long::class.java)
                .single()

        deliveries.retry(deliveryId, 2, next, now)

        val row = jdbc.sql("select attempts, next_attempt_at, updated_at from webhook_deliveries where id = ?").param(deliveryId).query().singleRow()
        assertEquals(2, row["attempts"])
        assertEquals(next, (row["next_attempt_at"] as Timestamp).toInstant())
        assertEquals(now, (row["updated_at"] as Timestamp).toInstant())
    }

    private companion object {
        const val CONCURRENT_READERS = 16
    }
}

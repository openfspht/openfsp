package org.openfsp.gateway

import org.junit.jupiter.api.Test
import org.openfsp.gateway.auth.PresentedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.auth.spi.ApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.IdempotentExecutions
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.Transition
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ResourceIds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
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
class PersistenceTest {
    @Autowired
    private lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var calendar: BusinessCalendar

    @Autowired
    private lateinit var idempotency: RunIdempotently

    @Autowired
    private lateinit var payments: Payments

    @Autowired
    private lateinit var journal: PaymentJournal

    @Autowired
    private lateinit var transactions: Transactions

    @Autowired
    private lateinit var manageKeys: ManageKeys

    @Autowired
    private lateinit var authentication: AuthenticateKey

    @Autowired
    private lateinit var apiKeys: ApiKeys

    @Autowired
    private lateinit var deliveries: Deliveries

    @Autowired
    private lateinit var health: EndpointHealth

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
        idempotencyRecord(principal, "old", calendar.now().minus(IdempotentExecutions.RETENTION).minus(Duration.ofDays(1)))

        val fresh = idempotency.run(IdempotencyScope(principal, "payments.create", "fresh"), "f", { "created" }) { RecordedResponse(201, it.toByteArray()) }
        assertEquals(Execution.Fresh("created"), fresh)

        assertEquals(2, idempotencyRecords(principal))
    }

    @Test
    fun `expired idempotency records are purged in bounded batches and recent ones kept`() {
        val principal = "purge-${UUID.randomUUID()}"
        val expired = calendar.now().minus(IdempotentExecutions.RETENTION).minus(Duration.ofDays(1))
        repeat(5) { idempotencyRecord(principal, "old-$it", expired.minusSeconds(it.toLong())) }
        idempotencyRecord(principal, "recent", calendar.now())

        val first = idempotency.purgeExpired(batch = 2, maxBatches = 2)
        val remainingAfterFirst = idempotencyRecords(principal)
        idempotency.purgeExpired(batch = 2, maxBatches = 2)

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
    fun `reconciliation takes the payments checked longest ago and skips a creation still in flight`() {
        val provider = "turns_${UUID.randomUUID().toString().take(8)}"
        val now = calendar.now()
        val first = payments.save(payment("turn-a-$provider", "turn-a-$provider").copy(provider = provider, createdAt = now.minusSeconds(120), updatedAt = now.minusSeconds(120)))
        val second = payments.save(payment("turn-b-$provider", "turn-b-$provider").copy(provider = provider, createdAt = now.minusSeconds(60), updatedAt = now.minusSeconds(60)))
        payments.save(payment("turn-c-$provider", "turn-c-$provider").copy(provider = provider, nextAction = null, createdAt = now.minusSeconds(30), updatedAt = now.minusSeconds(30)))

        assertEquals(listOf(first.id), payments.dueForReconciliation(setOf(provider), now, now.minusSeconds(300), 1).map { it.id })
        payments.markChecked(first.id, now)
        assertEquals(listOf(second.id, first.id), payments.dueForReconciliation(setOf(provider), now.plusSeconds(1), now.minusSeconds(300), 10).map { it.id })
    }

    @Test
    fun `a recorded fee is stored with its bearer, a zero fee stays distinct from an unknown one`() {
        val suffix = UUID.randomUUID().toString()
        val unknown = payments.save(payment("fee-unknown-$suffix", "fee-unknown-$suffix"))
        val zero = payments.save(payment("fee-zero-$suffix", "fee-zero-$suffix").copy(fee = ProviderFee(Money.htg(0), FeeBearer.PAYER)))
        val borne = payments.save(unknown.copy(fee = ProviderFee(Money.htg(2_500), FeeBearer.MERCHANT)))

        assertEquals(ProviderFee(Money.htg(0), FeeBearer.PAYER), payments.find(zero.id)!!.fee)
        assertEquals(ProviderFee(Money.htg(2_500), FeeBearer.MERCHANT), payments.find(borne.id)!!.fee)
        assertNull(unknown.fee)
    }

    @Test
    fun `a payment saved from a stale read is refused instead of overwriting a newer change`() {
        val suffix = UUID.randomUUID().toString()
        val stale = payments.save(payment("stale-$suffix", "stale-$suffix"))
        payments.save(stale.copy(providerReference = "TX-$suffix"))

        assertFailsWith<OptimisticLockingFailureException> { payments.save(stale.copy(description = "late")) }
        assertEquals("TX-$suffix", payments.find(stale.id)!!.providerReference)
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
    fun `keys issued at once for a new principal share one principal`() {
        val name = "principal-${UUID.randomUUID()}"
        val start = CyclicBarrier(CONCURRENT_READERS)
        val pool = Executors.newFixedThreadPool(CONCURRENT_READERS)
        try {
            (1..CONCURRENT_READERS).map { pool.submit { start.await(); manageKeys.issue(name, setOf(Scope.PAYMENTS_READ)) } }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        assertEquals(1, jdbc.sql("select count(*) from principals where name = ?").param(name).query(Int::class.java).single())
        assertEquals(CONCURRENT_READERS, jdbc.sql("select count(*) from api_keys k join principals p on p.id = k.principal_id where p.name = ?").param(name).query(Int::class.java).single())
    }

    @Test
    fun `concurrent abandonments on one endpoint are all counted`() {
        val endpoint = "abandoned-${UUID.randomUUID()}"
        val start = CyclicBarrier(CONCURRENT_READERS)
        val pool = Executors.newFixedThreadPool(CONCURRENT_READERS)
        try {
            (1..CONCURRENT_READERS).map { pool.submit<Int> { start.await(); transactions.inTransaction { health.abandoned(endpoint) } } }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        assertEquals(CONCURRENT_READERS, jdbc.sql("select consecutive_abandoned from webhook_endpoint_state where endpoint = ?").param(endpoint).query(Int::class.java).single())
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

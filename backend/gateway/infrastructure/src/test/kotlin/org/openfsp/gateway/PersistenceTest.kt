package org.openfsp.gateway

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.api.ManageAccounts
import org.junit.jupiter.api.Test
import org.openfsp.gateway.auth.PresentedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.auth.spi.ApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.IdempotentExecutions
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.RequestFingerprint
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.openfsp.gateway.idempotency.spi.IdempotencyRecords
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.ReconciliationDue
import org.openfsp.gateway.payment.ReconciliationTurn
import org.openfsp.gateway.payment.Transition
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.PaymentCredentials
import org.openfsp.gateway.provider.api.ManageProviderCredentials
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.providers.ConfiguredProviders
import org.openfsp.gateway.providers.persistence.ProviderCredentialStore
import org.openfsp.protocol.ProblemType
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
import java.util.concurrent.TimeUnit
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
    private lateinit var idempotencyRecords: IdempotencyRecords

    @Autowired
    private lateinit var health: EndpointHealth

    @Autowired
    private lateinit var accounts: ManageAccounts

    @Autowired
    private lateinit var credentials: ManageProviderCredentials

    @Autowired
    private lateinit var credentialStore: AccountCredentialStore

    @Autowired
    private lateinit var configuredProviders: ConfiguredProviders

    @Autowired
    private lateinit var providerCredentials: ProviderCredentialStore

    private fun keyRow(keyId: String): Map<String, Any?> = jdbc.sql("select digest, revoked_at, last_used_at from api_keys where id = ?").param(keyId).query().singleRow()

    private fun payment(
        reference: String,
        orderId: String,
    ) = Payment(ResourceIds.generate("pay"), MerchantAccount.DEFAULT, reference, Money.htg(1000), "mock_alpha", orderId, nextAction = NextAction.payerApproval, createdAt = calendar.now())

    private fun idempotencyRecord(
        principal: String,
        key: String,
        createdAt: Instant,
    ) {
        jdbc
            .sql("insert into idempotency_records (principal, operation, idempotency_key, fingerprint, fingerprint_version, response_status, response_body, created_at, claimed_at) values (?, 'payments.create', ?, 'f', 2, 201, '{}'::bytea, ?, ?)")
            .params(principal, key, Timestamp.from(createdAt), Timestamp.from(createdAt))
            .update()
    }

    private fun idempotencyRecords(principal: String): Int = jdbc.sql("select count(*) from idempotency_records where principal = ?").param(principal).query(Int::class.java).single()

    private fun walletCredentials(account: String) = credentials.save(account, "mock_wallet", mapOf("client-id" to "wallet-client", "client-secret" to "wallet-secret")).id

    private fun sealedRevision(revision: String): Boolean = jdbc.sql("select ciphertext is not null from provider_credential_revisions where id = ?").param(revision).query(Boolean::class.java).single()

    private fun walletPayment(
        account: String,
        revision: String,
    ) = payments.save(
        Payment(
            ResourceIds.generate("pay"),
            account,
            "INV-${UUID.randomUUID()}",
            Money.htg(1000),
            "mock_wallet",
            "order-${UUID.randomUUID()}",
            nextAction = NextAction.payerApproval,
            createdAt = calendar.now(),
            credentials = PaymentCredentials.Account(revision),
        ),
    )

    @Test
    fun `a retired credential revision is wiped only once no pending payment uses it`() {
        val account = accounts.open("purge-${UUID.randomUUID().toString().take(8)}").id
        val used = walletCredentials(account)
        val unused = walletCredentials(account)
        val active = walletCredentials(account)
        val payment = walletPayment(account, used)
        val after = calendar.now().plus(Duration.ofMinutes(1))

        transactions.inTransaction { credentialStore.purgeRetired(after, 1000) }
        assertTrue(sealedRevision(used))
        assertFalse(sealedRevision(unused))
        assertTrue(sealedRevision(active))

        payments.save(payments.find(payment.id)!!.copy(status = PaymentStatus.SUCCEEDED, completedAt = calendar.now(), nextAction = null))
        transactions.inTransaction { credentialStore.purgeRetired(after, 1000) }
        assertFalse(sealedRevision(used))
        assertEquals(PaymentCredentials.Account(used), payments.find(payment.id)!!.credentials)
    }

    @Test
    fun `a provider is built from a revision only while it is active or retired, within its own account`() {
        val account = accounts.open("built-${UUID.randomUUID().toString().take(8)}").id
        val other = accounts.open("other-${UUID.randomUUID().toString().take(8)}").id
        val first = walletCredentials(account)
        assertEquals(first, configuredProviders.active(account, "mock_wallet")!!.revision)
        assertNull(configuredProviders.ofRevision(other, "mock_wallet", first))

        val second = walletCredentials(account)
        assertEquals(second, configuredProviders.active(account, "mock_wallet")!!.revision)
        assertNotNull(configuredProviders.ofRevision(account, "mock_wallet", first))

        credentials.revoke(account, "mock_wallet", first)
        assertNull(configuredProviders.ofRevision(account, "mock_wallet", first))
        credentials.revoke(account, "mock_wallet", second)
        assertNull(configuredProviders.active(account, "mock_wallet"))
        assertNull(configuredProviders.ofRevision(account, "mock_wallet", second))
    }

    @Test
    fun `the revisions that can verify a notification of an account are its usable ones, active first, never another account's`() {
        val account = accounts.open("usable-${UUID.randomUUID().toString().take(8)}").id
        val other = accounts.open("foreign-${UUID.randomUUID().toString().take(8)}").id
        val revoked = walletCredentials(account)
        val retired = walletCredentials(account)
        val active = walletCredentials(account)
        walletCredentials(other)
        credentials.revoke(account, "mock_wallet", revoked)

        assertEquals(listOf(active, retired), configuredProviders.usableRevisions(account, "mock_wallet"))
        assertTrue(configuredProviders.usableRevisions(account, "mock_alpha").isEmpty())
    }

    @Test
    fun `a sealed secret copied onto another revision of the same account and provider does not open`() {
        val account = accounts.open("swap-${UUID.randomUUID().toString().take(8)}").id
        val first = walletCredentials(account)
        val second = walletCredentials(account)
        assertEquals("wallet-secret", providerCredentials.read(account, "mock_wallet", first)!!["client-secret"])
        val sealedOf = { revision: String -> jdbc.sql("select nonce, ciphertext from provider_credential_revisions where id = ?").param(revision).query().singleRow() }
        val (sealedFirst, sealedSecond) = sealedOf(first) to sealedOf(second)
        val copy = { revision: String, sealed: Map<String, Any?> ->
            jdbc.sql("update provider_credential_revisions set nonce = ?, ciphertext = ? where id = ?").params(sealed.getValue("nonce")!!, sealed.getValue("ciphertext")!!, revision).update()
        }
        copy(first, sealedSecond)
        copy(second, sealedFirst)

        listOf(first, second).forEach { revision ->
            assertEquals(ProblemType.PROVIDER_UNAVAILABLE, assertFailsWith<OpenFspException> { providerCredentials.read(account, "mock_wallet", revision) }.type)
        }
    }

    @Test
    fun `a payment cannot reference the credential revision of another account`() {
        val owner = accounts.open("owner-${UUID.randomUUID().toString().take(8)}").id
        val intruder = accounts.open("intruder-${UUID.randomUUID().toString().take(8)}").id
        val revision = walletCredentials(owner)

        assertFailsWith<DataIntegrityViolationException> { walletPayment(intruder, revision) }
        assertEquals(PaymentCredentials.Account(revision), walletPayment(owner, revision).credentials)
    }

    @Test
    fun `an idempotency lease is fenced by its generation in the database`() {
        val scope = IdempotencyScope("fence-${UUID.randomUUID()}", "payments.create", "key")
        val start = calendar.now()
        val later = start.plus(IdempotentExecutions.LEASE).plusSeconds(1)
        transactions.inTransaction { assertTrue(idempotencyRecords.insert(scope, "f", RequestFingerprint.VERSION, start)) }
        val first = transactions.inTransaction { idempotencyRecords.find(scope)!!.generation }

        assertTrue(transactions.inTransaction { idempotencyRecords.takeOver(scope, "f", later, later.minus(IdempotentExecutions.LEASE), first) })
        assertFalse(transactions.inTransaction { idempotencyRecords.takeOver(scope, "f", later, later.minus(IdempotentExecutions.LEASE), first) })
        assertFalse(transactions.inTransaction { idempotencyRecords.complete(scope, first, RecordedResponse(201, "first".toByteArray())) })
        transactions.inTransaction { idempotencyRecords.release(scope, first) }
        assertEquals(first + 1, transactions.inTransaction { idempotencyRecords.find(scope)!!.generation })

        assertTrue(transactions.inTransaction { idempotencyRecords.complete(scope, first + 1, RecordedResponse(201, "second".toByteArray())) })
        assertEquals(RecordedResponse(201, "second".toByteArray()), transactions.inTransaction { idempotencyRecords.find(scope)!!.response })
    }

    @Test
    fun `a claim no longer purges expired idempotency records`() {
        val principal = "purge-${UUID.randomUUID()}"
        idempotencyRecord(principal, "old", calendar.now().minus(IdempotentExecutions.RETENTION).minus(Duration.ofDays(1)))

        val fresh = idempotency.run(IdempotencyScope(principal, "payments.create", "fresh"), RequestFingerprint("f"), { "created" }) { RecordedResponse(201, it.toByteArray()) }
        assertEquals(Execution.Fresh("created"), fresh)

        assertEquals(2, idempotencyRecords(principal))
    }

    @Test
    fun `an idempotency record fingerprinted under an earlier rule is discarded, never compared`() {
        val scope = IdempotencyScope("earlier-${UUID.randomUUID()}", "payments.create", "key")
        transactions.inTransaction { assertTrue(idempotencyRecords.insert(scope, "f", RequestFingerprint.VERSION - 1, calendar.now())) }
        transactions.inTransaction { idempotencyRecords.complete(scope, IdempotencyRecord.FIRST_GENERATION, RecordedResponse(201, "earlier".toByteArray())) }

        assertEquals(Execution.Fresh("created"), idempotency.run(scope, RequestFingerprint("f"), { "created" }) { RecordedResponse(201, it.toByteArray()) })
        assertEquals(RequestFingerprint.VERSION, transactions.inTransaction { idempotencyRecords.find(scope)!!.fingerprintVersion })
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

    private fun due(
        provider: String,
        now: Instant,
    ) = ReconciliationDue(setOf(provider), now, now.minusSeconds(30), now.minusSeconds(300), now.plus(ReconciliationTurn.CLAIM))

    @Test
    fun `reconciliation takes the payments due longest ago, skips a creation still in flight and backs off after each attempt`() {
        val provider = "turns_${UUID.randomUUID().toString().take(8)}"
        val now = calendar.now()
        val first = payments.save(payment("turn-a-$provider", "turn-a-$provider").copy(provider = provider, createdAt = now.minusSeconds(120), updatedAt = now.minusSeconds(120)))
        val second = payments.save(payment("turn-b-$provider", "turn-b-$provider").copy(provider = provider, createdAt = now.minusSeconds(60), updatedAt = now.minusSeconds(60)))
        payments.save(payment("turn-c-$provider", "turn-c-$provider").copy(provider = provider, nextAction = null, createdAt = now.minusSeconds(30), updatedAt = now.minusSeconds(30)))

        val turn = payments.claimForReconciliation(due(provider, now), 1).single()
        assertEquals(first.id, turn.payment.id)
        assertEquals(0, turn.attempts)
        assertEquals(listOf(second.id), payments.claimForReconciliation(due(provider, now), 10).map { it.payment.id })

        payments.scheduleCheck(first.id, now.plus(turn.delayAfter), turn.nextAttempts)
        assertTrue(payments.claimForReconciliation(due(provider, now.plus(turn.delayAfter).minusSeconds(1)), 10).isEmpty())
        val again = payments.claimForReconciliation(due(provider, now.plus(turn.delayAfter)), 10).single()
        assertEquals(first.id to 1, again.payment.id to again.attempts)
        assertTrue(again.delayAfter > turn.delayAfter)
    }

    @Test
    fun `saving a payment keeps the reconciliation schedule set beside it`() {
        val provider = "keep_${UUID.randomUUID().toString().take(8)}"
        val now = calendar.now()
        val saved = payments.save(payment("keep-$provider", "keep-$provider").copy(provider = provider, createdAt = now.minusSeconds(120), updatedAt = now.minusSeconds(120)))
        payments.scheduleCheck(saved.id, now.plusSeconds(600), 3)

        payments.save(payments.find(saved.id)!!.copy(description = "changed after the schedule"))

        assertTrue(payments.claimForReconciliation(due(provider, now.plusSeconds(599)), 10).isEmpty())
        val turn = payments.claimForReconciliation(due(provider, now.plusSeconds(600)), 10).single()
        assertEquals(saved.id to 3, turn.payment.id to turn.attempts)
    }

    @Test
    fun `a payment is found by id only within its own merchant account`() {
        val payment = payments.save(payment("own-${UUID.randomUUID()}", "own-${UUID.randomUUID()}"))
        assertEquals(payment.id, payments.findOfAccount(MerchantAccount.DEFAULT, payment.id)?.id)
        assertNull(payments.findOfAccount("acct_foreign", payment.id))
    }

    @Test
    fun `two instances never claim the same payment for reconciliation`() {
        val provider = "skip_${UUID.randomUUID().toString().take(8)}"
        val now = calendar.now()
        val held = payments.save(payment("skip-a-$provider", "skip-a-$provider").copy(provider = provider, createdAt = now.minusSeconds(120), updatedAt = now.minusSeconds(120)))
        val free = payments.save(payment("skip-b-$provider", "skip-b-$provider").copy(provider = provider, createdAt = now.minusSeconds(60), updatedAt = now.minusSeconds(60)))
        val locked = CyclicBarrier(2)
        val claimed = CyclicBarrier(2)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val holder =
                executor.submit {
                    transactions.inTransaction {
                        payments.lock(held.id)
                        locked.await()
                        claimed.await(BARRIER_SECONDS, TimeUnit.SECONDS)
                    }
                }
            locked.await()
            val other = payments.claimForReconciliation(due(provider, now), 10).map { it.payment.id }
            claimed.await()
            holder.get()
            assertEquals(listOf(free.id), other)
        } finally {
            executor.shutdownNow()
        }
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
        val issued = manageKeys.issue(MerchantAccount.DEFAULT, "touch-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_READ))
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
        val issued = manageKeys.issue(MerchantAccount.DEFAULT, "race-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_READ))
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
            (1..CONCURRENT_READERS).map { pool.submit { start.await(); manageKeys.issue(MerchantAccount.DEFAULT, name, setOf(Scope.PAYMENTS_READ)) } }.forEach { it.get() }
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
        val issued = manageKeys.issue(MerchantAccount.DEFAULT, "audit-${UUID.randomUUID()}", setOf(Scope.PAYMENTS_WRITE))
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
        const val BARRIER_SECONDS = 10L
    }
}

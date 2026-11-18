package org.openfsp.gateway.idempotency

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.spi.stubs.InMemoryIdempotencyRecords
import org.openfsp.protocol.ProblemType
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class IdempotentExecutionsTest {
    private val clock = TestClock()
    private val records = InMemoryIdempotencyRecords()
    private val executions = IdempotentExecutions(records, ImmediateTransactions(), BusinessCalendar(clock))
    private val scope = IdempotencyScope("shop", "payments.create", "key-1")
    private var runs = 0

    private fun run(
        fingerprint: String = "f1",
        status: Int = 201,
    ): Execution<String> =
        executions.run(scope, RequestFingerprint(fingerprint), { runs += 1; "created $runs" }) { RecordedResponse(status, it.toByteArray()) }

    @Test
    fun `a completed request is replayed byte for byte without running again`() {
        assertEquals("created 1", assertIs<Execution.Fresh<String>>(run()).value)
        val replayed = assertIs<Execution.Replayed<String>>(run())
        assertEquals(201, replayed.response.status)
        assertContentEquals("created 1".toByteArray(), replayed.response.body)
        assertEquals(1, runs)
    }

    @Test
    fun `the same key with another body is refused`() {
        run()
        assertEquals(ProblemType.IDEMPOTENCY_KEY_REUSED, assertFailsWith<OpenFspException> { run(fingerprint = "f2") }.type)
    }

    @Test
    fun `a request still running is in progress until its lease expires, then taken over`() {
        val inner =
            executions.run(scope, RequestFingerprint("f1"), {
                assertFailsWith<OpenFspException> { run() }.also { assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, it.type) }
                "outer"
            }) { RecordedResponse(201, it.toByteArray()) }
        assertIs<Execution.Fresh<String>>(inner)

        val held = IdempotencyScope("shop", "payments.create", "key-2")
        records.insert(held, "f1", RequestFingerprint.VERSION, clock.instant())
        val refused = assertFailsWith<OpenFspException> { executions.run(held, RequestFingerprint("f1"), { "again" }) { RecordedResponse(201, it.toByteArray()) } }
        assertEquals(Duration.ofSeconds(1), refused.retryAfter)
        clock.advance(IdempotentExecutions.LEASE.plusSeconds(1))
        assertIs<Execution.Fresh<String>>(executions.run(held, RequestFingerprint("f1"), { "again" }) { RecordedResponse(201, it.toByteArray()) })
    }

    @Test
    fun `a failure releases the key so that the same request can run again`() {
        assertFailsWith<IllegalStateException> { executions.run(scope, RequestFingerprint("f1"), { error("upstream down") }) { RecordedResponse(201, ByteArray(0)) } }
        assertIs<Execution.Fresh<String>>(run())
    }

    @Test
    fun `expired records are purged in bounded batches`() {
        repeat(5) { records.insert(IdempotencyScope("shop", "payments.create", "old-$it"), "f", RequestFingerprint.VERSION, clock.instant()) }
        clock.advance(IdempotentExecutions.RETENTION.plusDays(1))
        records.insert(IdempotencyScope("shop", "payments.create", "recent"), "f", RequestFingerprint.VERSION, clock.instant())

        assertEquals(4, executions.purgeExpired(batch = 2, maxBatches = 2))
        assertEquals(1, executions.purgeExpired(batch = 2, maxBatches = 2))
        assertEquals(1, records.size)
    }

    private fun respond(value: String) = RecordedResponse(201, value.toByteArray())

    @Test
    fun `a worker whose lease was taken over cannot record its response`() {
        val first =
            executions.run(scope, RequestFingerprint("f1"), {
                clock.advance(IdempotentExecutions.LEASE.plusSeconds(1))
                assertIs<Execution.Fresh<String>>(executions.run(scope, RequestFingerprint("f1"), { "second" }, ::respond))
                "first"
            }, ::respond)
        assertEquals("first", assertIs<Execution.Fresh<String>>(first).value)
        assertContentEquals("second".toByteArray(), assertIs<Execution.Replayed<String>>(run()).response.body)
    }

    @Test
    fun `a worker whose lease was taken over cannot release the new lease when it fails`() {
        val firstStarted = CountDownLatch(1)
        val firstMayFail = CountDownLatch(1)
        val first =
            thread {
                runCatching {
                    executions.run(scope, RequestFingerprint("f1"), {
                        firstStarted.countDown()
                        firstMayFail.await()
                        throw IllegalStateException("first worker fails")
                    }, ::respond)
                }
            }
        firstStarted.await()
        clock.advance(IdempotentExecutions.LEASE.plusSeconds(1))
        val second =
            executions.run(scope, RequestFingerprint("f1"), {
                firstMayFail.countDown()
                first.join()
                assertEquals(1, records.size)
                assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, assertFailsWith<OpenFspException> { run() }.type)
                "second"
            }, ::respond)
        assertIs<Execution.Fresh<String>>(second)
        assertContentEquals("second".toByteArray(), assertIs<Execution.Replayed<String>>(run()).response.body)
    }

    @Test
    fun `a key past the retention is treated as a first request`() {
        run()
        clock.advance(IdempotentExecutions.RETENTION.plusSeconds(1))
        assertIs<Execution.Fresh<String>>(run())
        assertEquals(2, runs)
    }

    @Test
    fun `a record written under the legacy fingerprint still compares through it`() {
        records.insert(scope, "legacy-f1", LEGACY_VERSION, clock.instant())
        clock.advance(IdempotentExecutions.LEASE.plusSeconds(1))
        assertEquals(ProblemType.IDEMPOTENCY_KEY_REUSED, assertFailsWith<OpenFspException> { executions.run(scope, RequestFingerprint("f1"), { "x" }, ::respond) }.type)
        assertIs<Execution.Fresh<String>>(executions.run(scope, RequestFingerprint("f1", legacy = "legacy-f1"), { "x" }, ::respond))
    }

    private companion object {
        const val LEGACY_VERSION = 1
    }
}

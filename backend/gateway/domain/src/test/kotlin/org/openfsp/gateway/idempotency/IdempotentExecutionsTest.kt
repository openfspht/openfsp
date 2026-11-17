package org.openfsp.gateway.idempotency

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.idempotency.api.Execution
import org.openfsp.gateway.idempotency.spi.stubs.InMemoryIdempotencyRecords
import org.openfsp.protocol.ProblemType
import java.time.Duration
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
        executions.run(scope, fingerprint, { runs += 1; "created $runs" }) { RecordedResponse(status, it.toByteArray()) }

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
            executions.run(scope, "f1", {
                assertFailsWith<OpenFspException> { run() }.also { assertEquals(ProblemType.IDEMPOTENCY_REQUEST_IN_PROGRESS, it.type) }
                "outer"
            }) { RecordedResponse(201, it.toByteArray()) }
        assertIs<Execution.Fresh<String>>(inner)

        val held = IdempotencyScope("shop", "payments.create", "key-2")
        records.insert(held, "f1", clock.instant())
        val refused = assertFailsWith<OpenFspException> { executions.run(held, "f1", { "again" }) { RecordedResponse(201, it.toByteArray()) } }
        assertEquals(Duration.ofSeconds(1), refused.retryAfter)
        clock.advance(IdempotentExecutions.LEASE.plusSeconds(1))
        assertIs<Execution.Fresh<String>>(executions.run(held, "f1", { "again" }) { RecordedResponse(201, it.toByteArray()) })
    }

    @Test
    fun `a failure releases the key so that the same request can run again`() {
        assertFailsWith<IllegalStateException> { executions.run(scope, "f1", { error("upstream down") }) { RecordedResponse(201, ByteArray(0)) } }
        assertIs<Execution.Fresh<String>>(run())
    }

    @Test
    fun `expired records are purged in bounded batches`() {
        repeat(5) { records.insert(IdempotencyScope("shop", "payments.create", "old-$it"), "f", clock.instant()) }
        clock.advance(IdempotentExecutions.RETENTION.plusDays(1))
        records.insert(IdempotencyScope("shop", "payments.create", "recent"), "f", clock.instant())

        assertEquals(4, executions.purgeExpired(batch = 2, maxBatches = 2))
        assertEquals(1, executions.purgeExpired(batch = 2, maxBatches = 2))
        assertEquals(1, records.size)
    }
}

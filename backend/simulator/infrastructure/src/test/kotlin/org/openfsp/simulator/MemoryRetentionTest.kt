package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.dialects.openfsp.IdempotencyScope
import org.openfsp.simulator.dialects.openfsp.IdempotentStart
import org.openfsp.simulator.dialects.openfsp.OpenFspIdempotency
import org.openfsp.simulator.dialects.openfsp.RecordedResponse
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.memory.MemoryRequestJournal
import org.openfsp.simulator.space.SpaceId
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MemoryRetentionTest {
    private val start = Instant.parse("2026-11-11T15:00:00Z")

    @Test
    fun `the request journal keeps only the most recent entries of a space`() {
        val journal = MemoryRequestJournal(capacity = 3)
        repeat(5) { journal.record(SpaceId.DEFAULT, JournalEntry("mock_openfsp", start.plusSeconds(it.toLong()), "GET", "/mock_openfsp/v1/payments/$it", "")) }

        assertEquals(listOf("/2", "/3", "/4"), journal.entries(SpaceId.DEFAULT, null).map { it.path.removePrefix("/mock_openfsp/v1/payments") })
    }

    @Test
    fun `an idempotency record older than the purge horizon is forgotten`() {
        var now = start
        val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId) = this

                override fun instant() = now
            }
        val idempotency = OpenFspIdempotency(JsonMapper.builder().build(), SimulationClock(clock))
        val old = IdempotencyScope(SpaceId.DEFAULT, "mock_openfsp", "api-key", "old")
        val recent = IdempotencyScope(SpaceId.DEFAULT, "mock_openfsp", "api-key", "recent")
        idempotency.start(old, "f") { true }
        idempotency.finish(old, "f", RecordedResponse(201, "{}", null))
        now = now.plus(Duration.ofHours(1))
        idempotency.start(recent, "f") { true }
        idempotency.finish(recent, "f", RecordedResponse(201, "{}", null))

        idempotency.purge(SpaceId.DEFAULT, start.plus(Duration.ofMinutes(30)))

        assertEquals(IdempotentStart.Fresh, idempotency.start(old, "f") { true })
        assertIs<IdempotentStart.Replay>(idempotency.start(recent, "f") { true })
    }
}

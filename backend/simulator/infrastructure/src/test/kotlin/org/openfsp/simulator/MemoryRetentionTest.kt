package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.control.SimulationControl
import org.openfsp.simulator.dialects.openfsp.IdempotencyScope
import org.openfsp.simulator.dialects.openfsp.IdempotentStart
import org.openfsp.simulator.dialects.openfsp.OpenFspIdempotency
import org.openfsp.simulator.dialects.openfsp.RecordedResponse
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.memory.MemoryRequestJournal
import org.openfsp.simulator.memory.MemoryScenarioBook
import org.openfsp.simulator.memory.MemorySimulatedPayments
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.retention.RetentionSweep
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.openfsp.simulator.operator.Accounts
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MemoryRetentionTest {
    private class MovableClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val start = Instant.parse("2026-11-11T15:00:00Z")
    private val time = MovableClock(start)
    private val clock = SimulationClock(time)
    private val idempotency = OpenFspIdempotency(JsonMapper.builder().build(), clock)

    private fun record(key: String): IdempotencyScope {
        val scope = IdempotencyScope("mock_openfsp", "api-key", key)
        idempotency.start(scope, "f") { true }
        idempotency.finish(scope, "f", RecordedResponse(201, "{}", null))
        return scope
    }

    @Test
    fun `the request journal keeps only the most recent entries`() {
        val journal = MemoryRequestJournal(capacity = 3)
        repeat(5) { journal.record(JournalEntry("mock_openfsp", start.plusSeconds(it.toLong()), "GET", "/mock_openfsp/v1/payments/$it", "")) }

        assertEquals(listOf("/2", "/3", "/4"), journal.entries(null).map { it.path.removePrefix("/mock_openfsp/v1/payments") })
    }

    @Test
    fun `an idempotency record older than the purge horizon is forgotten`() {
        val old = record("old")
        time.now = start.plus(Duration.ofHours(1))
        val recent = record("recent")

        idempotency.purge(start.plus(Duration.ofMinutes(30)))

        assertEquals(IdempotentStart.Fresh, idempotency.start(old, "f") { true })
        assertIs<IdempotentStart.Replay>(idempotency.start(recent, "f") { true })
    }

    @Test
    fun `the sweep forgets payments and idempotency records older than the retention`() {
        val payments = MemorySimulatedPayments()
        val profiles = OperatorProfiles { null }
        val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
        val settlement = OperatorSettlement(payments, profiles, { _, _, _ -> true }, { _, _ -> }, clock, settings, {})
        val control = SimulationControl(payments, MemoryScenarioBook(), MemoryRequestJournal(capacity = 3), settlement, listOf(idempotency), profiles)
        val properties = SimulatorProperties(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, emptyMap(), retention = Duration.ofHours(2))
        val sweep = RetentionSweep(control, clock, properties)
        val held = { reference: String -> payments.save(SimulatedPayment("mock_openfsp", "pay_$reference", reference, "100.00", null, null, Scenario.PENDING_FOREVER, time.now, time.now.plusSeconds(900), account = Accounts.PUBLISHED)) }

        held("old")
        val old = record("old")
        time.now = start.plus(Duration.ofMinutes(90))
        held("recent")
        val recent = record("recent")
        time.now = start.plus(Duration.ofMinutes(121))

        sweep.sweep()

        assertEquals(listOf("recent"), control.payments().map { it.reference })
        assertEquals(IdempotentStart.Fresh, idempotency.start(old, "f") { true })
        assertIs<IdempotentStart.Replay>(idempotency.start(recent, "f") { true })
    }
}

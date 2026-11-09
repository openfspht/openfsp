package org.openfsp.simulator

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class SimulatedPayment(
    val operator: String,
    val paymentId: String,
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val callbackUrl: String?,
    val scenario: Scenario,
    val createdAt: Instant,
    val expiresAt: Instant,
    val outcome: Outcome? = null,
    val reason: String? = null,
    val reportedAmount: String? = null,
    val callbacksSent: Int = 0,
    val lookups: Int = 0,
)

data class StatementLine(
    val reference: String,
    val orderId: String?,
    val type: String,
    val amount: String,
    val bookedAt: String,
)

data class OperatorStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val entries: List<StatementLine>,
)

data class JournalEntry(
    val operator: String,
    val receivedAt: Instant,
    val method: String,
    val path: String,
    val body: String,
)

private val HAITI: ZoneId = ZoneId.of("America/Port-au-Prince")

@Component
class SimulatorState(
    private val clock: Clock,
) {
    private val scenarios = ConcurrentHashMap<Pair<String, String>, Scenario>()
    private val defaults = ConcurrentHashMap<String, Scenario>()
    private val payments = ConcurrentHashMap<String, SimulatedPayment>()
    private val journal = CopyOnWriteArrayList<JournalEntry>()
    private val sequence = AtomicLong()
    private val clockOffset = AtomicLong()
    private val statements = ConcurrentHashMap<Pair<String, LocalDate>, MutableList<StatementLine>>()
    private val closedDays = ConcurrentHashMap.newKeySet<Pair<String, LocalDate>>()

    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), HAITI)

    fun book(
        operator: String,
        line: StatementLine,
        on: LocalDate = localNow().toLocalDate(),
    ) {
        statements.computeIfAbsent(operator to on) { CopyOnWriteArrayList() } += line
    }

    fun closeDay(
        operator: String,
        day: LocalDate,
    ) {
        closedDays += operator to day
    }

    fun statement(
        operator: String,
        day: LocalDate,
    ) = OperatorStatement(day, (operator to day) in closedDays, statements[operator to day].orEmpty().toList())

    fun now(): Instant = clock.instant().plusSeconds(clockOffset.get())

    fun advanceClock(by: Duration) {
        clockOffset.addAndGet(by.seconds)
    }

    fun declare(
        operator: String,
        orderId: String,
        scenario: Scenario,
    ) {
        scenarios[operator to orderId] = scenario
    }

    fun scenarioFor(
        operator: String,
        orderId: String,
    ): Scenario = scenarios[operator to orderId] ?: defaults[operator] ?: Scenario.SUCCESS

    fun declareDefault(
        operator: String,
        scenario: Scenario,
    ) {
        defaults[operator] = scenario
    }

    fun defaultFor(operator: String): Scenario = defaults[operator] ?: Scenario.SUCCESS

    fun nextPaymentId(prefix: String): String = "%s-%08d".format(prefix, sequence.incrementAndGet())

    fun save(payment: SimulatedPayment): SimulatedPayment = payment.also { payments[it.paymentId] = it }

    fun find(paymentId: String): SimulatedPayment? = payments[paymentId]

    fun forget(paymentId: String) {
        payments.remove(paymentId)
    }

    fun findByOrder(
        operator: String,
        orderId: String,
    ): SimulatedPayment? = payments.values.firstOrNull { it.operator == operator && it.orderId == orderId }

    fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment? = payments.computeIfPresent(paymentId) { _, current -> change(current) }

    fun payments(): List<SimulatedPayment> = payments.values.sortedByDescending { it.createdAt }

    fun record(entry: JournalEntry) {
        journal += entry
    }

    fun journal(operator: String?): List<JournalEntry> = journal.filter { operator == null || it.operator == operator }

    fun reset() {
        scenarios.clear()
        defaults.clear()
        payments.clear()
        journal.clear()
        sequence.set(0)
        clockOffset.set(0)
        statements.clear()
        closedDays.clear()
    }
}

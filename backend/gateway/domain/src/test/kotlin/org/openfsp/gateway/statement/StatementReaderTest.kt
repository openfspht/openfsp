package org.openfsp.gateway.statement

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.StatementPeriod
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StatementReaderTest {
    private val calendar = BusinessCalendar(TestClock())
    private val payments = InMemoryPayments()
    private val reader = StatementReader(InMemoryProviders(listOf(ProviderStub(), ProviderStub(id = "mock_beta"))), payments, calendar)
    private val day = LocalDate.parse("2026-09-28")
    private val period = StatementPeriod(day, day)

    private fun settled(
        id: String,
        completedAt: String,
        status: PaymentStatus = PaymentStatus.SUCCEEDED,
        provider: String = "mock_alpha",
    ) = payments.save(
        Payment(
            id = id,
            reference = "INV-$id",
            amount = Money.htg(125_000),
            provider = provider,
            providerOrderId = "order-$id",
            status = status,
            completedAt = Instant.parse(completedAt),
            failureReason = FailureReason.DECLINED.takeIf { status == PaymentStatus.FAILED },
            createdAt = Instant.parse(completedAt).minusSeconds(60),
        ),
    )

    @Test
    fun `an entry is a succeeded payment of the provider, booked when it completed`() {
        settled("pay_1", "2026-09-28T14:33:02Z")
        settled("pay_failed", "2026-09-28T15:00:00Z", PaymentStatus.FAILED)
        settled("pay_expired", "2026-09-28T15:00:00Z", PaymentStatus.EXPIRED)
        settled("pay_other", "2026-09-28T15:00:00Z", provider = "mock_beta")
        payments.save(Payment("pay_pending", "INV-pending", Money.htg(1000), "mock_alpha", "order-pending", createdAt = Instant.parse("2026-09-28T15:00:00Z")))

        val page = reader.page("mock_alpha", period, 50, null)

        assertEquals(
            listOf(StatementEntry("mock_alpha", day, Instant.parse("2026-09-28T14:33:02Z"), Money.htg(125_000), null, null, "pay_1", "INV-pay_1")),
            page.entries,
        )
        assertNull(page.next)
    }

    @Test
    fun `the business day is the Port-au-Prince date, in summer and in winter time`() {
        settled("pay_summer_before", "2026-09-28T03:59:59Z")
        settled("pay_summer_first", "2026-09-28T04:00:00Z")
        settled("pay_summer_last", "2026-09-29T03:59:59Z")
        settled("pay_winter_before", "2026-12-01T04:59:59Z")
        settled("pay_winter_first", "2026-12-01T05:00:00Z")
        val winter = LocalDate.parse("2026-12-01")

        assertEquals(listOf("pay_summer_first", "pay_summer_last"), reader.page("mock_alpha", period, 50, null).entries.map { it.paymentId })
        assertEquals(listOf("pay_winter_first"), reader.page("mock_alpha", StatementPeriod(winter, winter), 50, null).entries.map { it.paymentId })
        assertEquals(listOf(winter.minusDays(1)), reader.page("mock_alpha", StatementPeriod(winter.minusDays(1), winter.minusDays(1)), 50, null).entries.map { it.businessDate })
    }

    @Test
    fun `pages follow the booking time, then the payment id, without skipping or repeating`() {
        settled("pay_c", "2026-09-28T12:00:00Z")
        settled("pay_b", "2026-09-28T12:00:00Z")
        settled("pay_a", "2026-09-28T13:00:00Z")

        val first = reader.page("mock_alpha", period, 2, null)
        val second = reader.page("mock_alpha", period, 2, first.next)

        assertEquals(listOf("pay_b", "pay_c"), first.entries.map { it.paymentId })
        assertEquals(StatementCursor(Instant.parse("2026-09-28T12:00:00Z"), "pay_c"), first.next)
        assertEquals(listOf("pay_a"), second.entries.map { it.paymentId })
        assertNull(second.next)
    }

    @Test
    fun `the whole period is read across batches`() {
        val start = Instant.parse("2026-09-28T12:00:00Z")
        repeat(ENTRIES) { settled("pay_%04d".format(it), start.plusSeconds(it.toLong()).toString()) }

        val all = reader.all("mock_alpha", StatementPeriod(day.minusDays(30), day)).map { it.paymentId }.toList()

        assertEquals((0 until ENTRIES).map { "pay_%04d".format(it) }, all)
    }

    @Test
    fun `a limit out of bounds or a provider the gateway does not serve is refused`() {
        listOf(0, 101).forEach { limit -> assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { reader.page("mock_alpha", period, limit, null) }.type) }
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { reader.page("mock_unknown", period, 50, null) }.type)
        assertEquals(ProblemType.INVALID_FIELD, assertFailsWith<OpenFspException> { reader.all("mock_unknown", period) }.type)
    }

    private companion object {
        const val ENTRIES = 1_203
    }
}

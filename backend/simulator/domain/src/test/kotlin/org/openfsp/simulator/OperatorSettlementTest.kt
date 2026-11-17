package org.openfsp.simulator

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.common.SimulationSettings
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.operator.spi.OperatorProfiles
import org.openfsp.simulator.payment.Outcome
import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.payment.spi.stubs.InMemorySimulatedPayments
import org.openfsp.simulator.scenario.Scenario
import org.openfsp.simulator.settlement.OperatorCallback
import org.openfsp.simulator.settlement.OperatorSettlement
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.statement.spi.stubs.InMemoryStatementBook
import java.time.Clock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class OperatorSettlementTest {
    private val space = SpaceId.DEFAULT
    private val openFsp = OperatorProfile("mock_openfsp", Dialect.OPENFSP, "MO", lookup = true, callbacks = true, idempotentByOrder = true, statements = true, returnUrl = null)
    private val profiles = OperatorProfiles { id -> openFsp.takeIf { it.id == id } }
    private val stored = InMemorySimulatedPayments()
    private var beforeNextUpdate: (() -> Unit)? = null
    private val payments =
        object : SimulatedPayments by stored {
            override fun update(
                space: SpaceId,
                paymentId: String,
                change: (SimulatedPayment) -> SimulatedPayment,
            ): SimulatedPayment? {
                val interleaved = beforeNextUpdate
                beforeNextUpdate = null
                interleaved?.invoke()
                return stored.update(space, paymentId, change)
            }
        }
    private val statements = InMemoryStatementBook()
    private val sent = mutableListOf<OperatorCallback>()
    private val clock = SimulationClock(Clock.systemUTC())
    private val settings = SimulationSettings(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO)
    private val settlement = OperatorSettlement(payments, profiles, statements, { _, _, callback -> sent.add(callback) }, { _, task -> task() }, clock, settings)

    private fun pending(
        paymentId: String,
        scenario: Scenario = Scenario.SUCCESS,
    ): SimulatedPayment {
        val now = clock.now()
        return stored.save(space, SimulatedPayment("mock_openfsp", paymentId, "ref-$paymentId", "100.00", null, "https://gw.example/callbacks", scenario, now, now.plusSeconds(900)))
    }

    @Test
    fun `two resolutions racing on one payment book one statement line and send one callback`() {
        pending("pay_race")
        beforeNextUpdate = { settlement.resolve(space, "pay_race", Outcome.SUCCESSFUL, null) }

        settlement.resolve(space, "pay_race", Outcome.FAILED, "DECLINED")

        assertEquals(Outcome.SUCCESSFUL, stored.find(space, "pay_race")?.outcome)
        assertEquals(1, statements.lines(space, "mock_openfsp", clock.today()).size)
        assertEquals(1, sent.size)
    }

    @Test
    fun `each duplicated callback carries the next delivery attempt`() {
        pending("pay_duplicate", Scenario.DUPLICATE_CALLBACK)

        settlement.resolve(space, "pay_duplicate", Outcome.SUCCESSFUL, null)

        assertEquals(listOf(0, 1, 2), sent.map { it.payment.callbacksSent })
        assertEquals(3, stored.find(space, "pay_duplicate")?.callbacksSent)
    }
}

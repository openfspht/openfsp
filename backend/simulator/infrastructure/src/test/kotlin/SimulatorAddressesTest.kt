package org.openfsp.simulator

import org.openfsp.simulator.shared.configuration.SimulatorProperties
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimulatorAddressesTest {
    private fun properties(publicBaseUrl: String) = SimulatorProperties(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, emptyMap(), publicBaseUrl)

    @Test
    fun `the simulator refuses to start without its public address`() {
        listOf("\${openfsp.simulator-url}", "", "simulator.example").forEach { address ->
            assertTrue(assertFailsWith<IllegalStateException>(address) { properties(address) }.message!!.contains("OPENFSP_SIMULATOR_URL"))
        }
        properties("https://simulator.example")
    }
}

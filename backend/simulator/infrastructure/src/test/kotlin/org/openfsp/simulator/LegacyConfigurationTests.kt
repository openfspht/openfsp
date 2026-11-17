package org.openfsp.simulator

import org.junit.jupiter.api.Test
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import kotlin.test.assertEquals

@SpringBootTest(properties = ["simulator.public-base-url=http://legacy.example:9", "simulator.response-delay=0ms", "simulator.settle-after=7s", "simulator.hang-duration=2500ms", "simulator.late-callback-delay=1h"])
class LegacyConfigurationTests {
    @Autowired
    private lateinit var properties: SimulatorProperties

    @Test
    fun `the keys used before the openfsp prefix still configure a running simulator`() {
        assertEquals("http://legacy.example:9", properties.publicBaseUrl)
        assertEquals(Duration.ZERO, properties.responseDelay)
        assertEquals(Duration.ofSeconds(7), properties.settleAfter)
        assertEquals(Duration.ofMillis(2500), properties.hangDuration)
        assertEquals(Duration.ofHours(1), properties.lateCallbackDelay)
    }
}

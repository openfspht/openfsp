package org.openfsp.simulator

import org.junit.jupiter.api.Test
import org.openfsp.simulator.control.ControlScope
import org.openfsp.simulator.control.api.ControlSimulation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "openfsp.simulator.response-delay=0ms",
        "server.address=127.0.0.1", "openfsp.simulator-url=http://simulator.test", "openfsp.gateway-url=http://gateway.test",
        "openfsp.simulator.conformance.max-active-runs=2",
    ],
)
class ConformanceRunLimitTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var control: ControlSimulation

    private val client by lazy { localClient(port) }

    @Test
    fun `a preparation beyond the active runs the simulator admits is refused with rate-limited`() {
        control.reset(ControlScope.Instance)
        repeat(2) { assertEquals(201, client.post().uri("/mock_openfsp/conformance").retrieve().toBodilessEntity().statusCode.value()) }

        val refused = client.post().uri("/mock_openfsp/conformance").retrieve().toEntity(Map::class.java)

        assertEquals(429, refused.statusCode.value())
        assertEquals("https://openfsp.org/problems/rate-limited", refused.body!!["type"])
        assertEquals("60", refused.headers.getFirst("Retry-After"))
    }
}
